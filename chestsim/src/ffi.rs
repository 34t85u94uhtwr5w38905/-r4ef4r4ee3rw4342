//! C interface used by the Python trainer through `ctypes`.
use crate::env::{PARAM_CNAMES, STAT_NAMES, VecEnv, ACT_DIMS, ACT_HEADS, OBS_DIM, PRIV_DIM, STATS_DIM};
use std::slice;

#[no_mangle]
pub extern "C" fn cs_obs_dim() -> i32 {
    OBS_DIM as i32
}

#[no_mangle]
pub extern "C" fn cs_priv_dim() -> i32 {
    PRIV_DIM as i32
}

#[no_mangle]
pub extern "C" fn cs_stats_dim() -> i32 {
    STATS_DIM as i32
}

#[no_mangle]
pub extern "C" fn cs_act_heads() -> i32 {
    ACT_HEADS as i32
}

/// # Safety
/// `out` must point to `cs_act_heads()` writable ints.
#[no_mangle]
pub unsafe extern "C" fn cs_act_dims(out: *mut i32) {
    slice::from_raw_parts_mut(out, ACT_HEADS).copy_from_slice(&ACT_DIMS);
}

#[no_mangle]
pub extern "C" fn cs_vec_new(n: i32, seed: u64) -> *mut VecEnv {
    Box::into_raw(Box::new(VecEnv::new(n.max(1) as usize, seed)))
}

/// # Safety
/// `p` must come from `cs_vec_new` and not be used afterwards.
#[no_mangle]
pub unsafe extern "C" fn cs_vec_free(p: *mut VecEnv) {
    if !p.is_null() {
        drop(Box::from_raw(p));
    }
}

/// # Safety
/// `p` must be a live pointer from `cs_vec_new`.
#[no_mangle]
pub unsafe extern "C" fn cs_vec_set_param(p: *mut VecEnv, id: i32, value: f64) -> i32 {
    (*p).set_param(id, value) as i32
}

/// # Safety
/// `obs` and `privs` must hold n*obs_dim and n*priv_dim floats.
#[no_mangle]
pub unsafe extern "C" fn cs_vec_reset(p: *mut VecEnv, obs: *mut f32, privs: *mut f32) {
    let v = &mut *p;
    let n = v.envs.len();
    v.reset(slice::from_raw_parts_mut(obs, n * OBS_DIM), slice::from_raw_parts_mut(privs, n * PRIV_DIM));
}

/// # Safety
/// Every buffer must hold one row per environment of the matching width.
#[no_mangle]
pub unsafe extern "C" fn cs_vec_step(p: *mut VecEnv, actions: *const i32, obs: *mut f32, privs: *mut f32, rew: *mut f32, done: *mut u8, fobs: *mut f32, fpriv: *mut f32) {
    let v = &mut *p;
    let n = v.envs.len();
    v.step(
        slice::from_raw_parts(actions, n * ACT_HEADS),
        slice::from_raw_parts_mut(obs, n * OBS_DIM),
        slice::from_raw_parts_mut(privs, n * PRIV_DIM),
        slice::from_raw_parts_mut(rew, n),
        slice::from_raw_parts_mut(done, n),
        slice::from_raw_parts_mut(fobs, n * OBS_DIM),
        slice::from_raw_parts_mut(fpriv, n * PRIV_DIM),
    );
}

/// # Safety
/// `out` must hold `cs_stats_dim()` doubles.
#[no_mangle]
pub unsafe extern "C" fn cs_vec_take_stats(p: *mut VecEnv, out: *mut f64) {
    (*p).take_stats(slice::from_raw_parts_mut(out, STATS_DIM));
}

#[no_mangle]
pub extern "C" fn cs_param_count() -> i32 {
    PARAM_CNAMES.len() as i32
}

/// Name of parameter `i`, a NUL-terminated string that lives forever.
#[no_mangle]
pub extern "C" fn cs_param_name(i: i32) -> *const u8 {
    PARAM_CNAMES[i as usize].as_ptr()
}

#[no_mangle]
pub extern "C" fn cs_stat_count() -> i32 {
    STAT_NAMES.len() as i32
}

/// # Safety
/// `out` must hold n*act_heads ints.
#[no_mangle]
pub unsafe extern "C" fn cs_vec_teacher(p: *mut VecEnv, out: *mut i32) {
    let v = &mut *p;
    let n = v.envs.len();
    v.teacher(slice::from_raw_parts_mut(out, n * ACT_HEADS));
}
