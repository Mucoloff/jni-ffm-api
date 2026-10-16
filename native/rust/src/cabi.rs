//! Hand-written flat C-ABI over the FNV core: only the loop parts (transform, batch).
//! The lifecycle/hash symbols (nat_fnv_new/free/update/digest/reset/hash) are generated
//! into native_generated.rs from native-api.json. Pointers are *mut c_void so the
//! generated thunks pass addresses uniformly; the FFM downcalls (ADDRESS) are unaffected.

use crate::fnv::Fnv;
use std::os::raw::c_void;

/// Hash n buffers in a single call — amortizes the JVM<->native crossing cost.
///
/// # Safety
///
/// - `ptrs` must point to an array of at least `n` valid pointer values (`*const u8`).
/// - `lens` must point to an array of at least `n` valid `usize` lengths.
/// - `out` must point to writable memory for at least `n` `u64` hash digests.
/// - Each buffer `ptrs[i]` with non-zero `lens[i]` must remain valid and readable for the duration of the call.
#[no_mangle]
pub unsafe extern "C" fn nat_fnv_hash_batch(ptrs: *mut c_void, lens: *mut c_void, out: *mut c_void, n: usize) {
    if ptrs.is_null() || lens.is_null() || out.is_null() || n == 0 {
        return;
    }
    let ptrs = ptrs as *const *const u8;
    let lens = lens as *const usize;
    let out = out as *mut u64;

    for i in 0..n {
        // SAFETY: Caller guarantees ptrs and lens point to at least n valid entries.
        let p = unsafe { *ptrs.add(i) };
        let l = unsafe { *lens.add(i) };
        let h = if p.is_null() || l == 0 {
            Fnv::hash(&[])
        } else {
            // SAFETY: Caller guarantees buffer p is readable for at least l bytes.
            let slice = unsafe { std::slice::from_raw_parts(p, l) };
            Fnv::hash(slice)
        };
        // SAFETY: Caller guarantees out points to at least n writable u64 entries.
        unsafe {
            *out.add(i) = h;
        }
    }
}

/// Memory-bound, in-place transform: data[i] += add.
///
/// # Safety
///
/// - If `len > 0`, `data` must be non-null, aligned for `u8`, and point to at least `len` valid writable bytes.
#[no_mangle]
pub unsafe extern "C" fn nat_transform(data: *mut c_void, len: usize, add: u8) {
    if data.is_null() || len == 0 {
        return;
    }
    // SAFETY: data is non-null and len > 0, validated by caller to point to writable memory.
    let slice = unsafe { std::slice::from_raw_parts_mut(data as *mut u8, len) };
    for b in slice {
        *b = b.wrapping_add(add);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_nat_transform_in_place() {
        let mut buf = [10u8, 20, 30, 40];
        // SAFETY: buf is valid stack memory of 4 bytes.
        unsafe {
            nat_transform(buf.as_mut_ptr() as *mut c_void, buf.len(), 5);
        }
        assert_eq!(buf, [15, 25, 35, 45]);
    }

    #[test]
    fn test_nat_fnv_hash_batch() {
        let b1 = b"abc";
        let b2 = b"defgh";
        let ptrs = [b1.as_ptr(), b2.as_ptr()];
        let lens = [b1.len(), b2.len()];
        let mut out = [0u64; 2];

        // SAFETY: All pointers and lengths match array sizes and point to valid buffers.
        unsafe {
            nat_fnv_hash_batch(
                ptrs.as_ptr() as *mut c_void,
                lens.as_ptr() as *mut c_void,
                out.as_mut_ptr() as *mut c_void,
                2,
            );
        }

        assert_eq!(out[0], Fnv::hash(b1));
        assert_eq!(out[1], Fnv::hash(b2));
    }
}
