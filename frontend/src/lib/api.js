// src/lib/api.js

const API_BASE = import.meta.env.VITE_API_BASE_URL || "/api/v1";

const ACCESS_TOKEN_KEY = "smu_access_token";
const REFRESH_TOKEN_KEY = "smu_refresh_token";

// ===== 세션 만료 이벤트(전역) =====
const SESSION_EXPIRED_EVENT = "smu_session_expired_v1";

function emit_session_expired(detail) {
  try {
    window.dispatchEvent(
      new CustomEvent(SESSION_EXPIRED_EVENT, { detail: detail || {} }),
    );
  } catch {}
}

export function on_session_expired(handler) {
  const h = (e) => handler?.(e?.detail || {});
  window.addEventListener(SESSION_EXPIRED_EVENT, h);
  return () => window.removeEventListener(SESSION_EXPIRED_EVENT, h);
}

// ===== 토큰 유틸 =====
export function get_access_token() {
  return localStorage.getItem(ACCESS_TOKEN_KEY) || null;
}

export function get_refresh_token() {
  return localStorage.getItem(REFRESH_TOKEN_KEY) || null;
}

export function set_tokens(access_token, refresh_token) {
  const norm = (t) => {
    if (!t) return null;
    const s = String(t).trim();
    return s.toLowerCase().startsWith("bearer ") ? s.slice(7).trim() : s;
  };

  const access = norm(access_token);
  const refresh = norm(refresh_token);

  if (access) localStorage.setItem(ACCESS_TOKEN_KEY, access);
  if (refresh) localStorage.setItem(REFRESH_TOKEN_KEY, refresh);
}

export function clear_tokens() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

export function is_logged_in() {
  return !!get_access_token();
}

// ===== 내부 공통 =====
function resolve_url(path) {
  if (typeof path === "string" && /^https?:\/\//.test(path)) return path;
  if (typeof path === "string") return `${API_BASE}${path}`;
  return path;
}

function make_init(init = {}, access_token) {
  const merged_headers = {
    ...(init.headers || {}),
    ...(access_token ? { Authorization: `Bearer ${access_token}` } : {}),
  };

  const has_body = init.body !== undefined && init.body !== null;

  return {
    method: "GET",
    ...init,
    credentials: "include",
    headers: has_body
      ? { "Content-Type": "application/json", ...merged_headers }
      : merged_headers,
  };
}

function is_reissue_call(path) {
  const p = String(path || "");
  return p.includes("/public/auth/reissue");
}

// ✅ 401이라도 LOGIN_FAILED면 만료로 보지 않음
function is_expired_response(res, data) {
  if (data?.status === "FAIL" && data?.errorCode === "EXPIRED_TOKEN")
    return true;
  if (res?.status === 401 && data?.errorCode !== "LOGIN_FAILED") return true;
  return false;
}

let reissue_promise = null;

async function do_reissue(prev_access_token) {
  const refresh_token = get_refresh_token();

  if (!refresh_token) {
    clear_tokens();
    emit_session_expired({ reason: "no_refresh_token" });
    throw Object.assign(
      new Error("세션이 만료되었습니다. 다시 로그인해 주세요."),
      { code: "EXPIRED_TOKEN" },
    );
  }

  const re_res = await fetch(resolve_url("/public/auth/reissue"), {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      accessToken: prev_access_token,
      refreshToken: refresh_token,
    }),
  });

  const re_data = await re_res.json().catch(() => null);

  if (!re_res.ok || re_data?.status === "FAIL") {
    clear_tokens();
    emit_session_expired({
      reason: "reissue_failed",
      status: re_res.status,
      raw: re_data,
    });
    throw Object.assign(
      new Error("세션이 만료되었습니다. 다시 로그인해 주세요."),
      { code: "EXPIRED_TOKEN" },
    );
  }

  const new_access = re_data?.data?.accessToken;
  const new_refresh = re_data?.data?.refreshToken;

  set_tokens(new_access, new_refresh);

  return {
    access_token: get_access_token(),
    refresh_token: get_refresh_token(),
  };
}

// ===== fetch 래퍼 (만료 시 reissue + 원요청 1회 재시도, 동시성 보호) =====
export async function apiFetch(path, init = {}) {
  let access_token = get_access_token();
  const doFetch = () => fetch(resolve_url(path), make_init(init, access_token));

  let res = await doFetch();

  let data = null;
  try {
    data = await res.clone().json();
  } catch (_) {}

  if (is_reissue_call(path)) return res;

  // ✅ 로그인 실패(학번/비번 불일치)는 reissue/세션만료 처리 금지
  if (data?.status === "FAIL" && data?.errorCode === "LOGIN_FAILED") return res;

  const expired = is_expired_response(res, data);
  if (!expired) return res;

  try {
    if (!reissue_promise) {
      reissue_promise = do_reissue(access_token).finally(() => {
        reissue_promise = null;
      });
    }

    const renewed = await reissue_promise;
    access_token = renewed.access_token;

    res = await fetch(resolve_url(path), make_init(init, access_token));

    // 재시도 후에도 만료/401이면 세션 종료 처리
    let retry_data = null;
    try {
      retry_data = await res.clone().json();
    } catch (_) {}

    if (is_expired_response(res, retry_data)) {
      clear_tokens();
      emit_session_expired({
        reason: "retry_still_expired",
        status: res.status,
        raw: retry_data,
      });
      throw Object.assign(
        new Error("세션이 만료되었습니다. 다시 로그인해 주세요."),
        { code: "EXPIRED_TOKEN" },
      );
    }

    return res;
  } catch (e) {
    if (e?.code === "EXPIRED_TOKEN") {
      clear_tokens();
      emit_session_expired({ reason: "expired_token_throw" });
    }
    throw e;
  }
}

// ===== JSON 헬퍼 =====
export async function apiJson(path, init) {
  const res = await apiFetch(path, init);
  const data = await res.json().catch(() => null);

  if (!res.ok || data?.status === "FAIL") {
    const msg = data?.message || "요청에 실패했습니다.";
    const code = data?.errorCode;
    const err = new Error(msg);
    err.code = code || (res.status === 401 ? "UNAUTHORIZED" : "ERROR");
    err.raw = data;
    err.status = res.status;

    // ✅ 여기서도 401 전부 만료로 치지 말고 LOGIN_FAILED는 제외
    if (
      err.code === "EXPIRED_TOKEN" ||
      (res.status === 401 && err.code !== "LOGIN_FAILED")
    ) {
      clear_tokens();
      emit_session_expired({
        reason: "apiJson_error",
        status: res.status,
        raw: data,
      });
    }

    throw err;
  }
  return data;
}

// ===== owner 전용 fetch (apiFetch 기반) - 토큰 reissue 포함 =====
async function owner_fetch_json(path, init = {}) {
  const res = await apiFetch(path, init);
  const data = await res.json().catch(() => null);

  if (!res.ok || data?.status === "FAIL") {
    const err = new Error(data?.message || "요청에 실패했습니다.");
    err.code =
      data?.errorCode || (res.status === 401 ? "UNAUTHORIZED" : "ERROR");
    err.status = res.status;
    err.raw = data;

    // ✅ 401 전부 만료로 치지 말고 LOGIN_FAILED는 제외
    if (
      err.code === "EXPIRED_TOKEN" ||
      (res.status === 401 && err.code !== "LOGIN_FAILED")
    ) {
      clear_tokens();
      emit_session_expired({
        reason: "owner_fetch_json_error",
        status: res.status,
        raw: data,
      });
    }

    throw err;
  }

  return data;
}

// ===== 인증 =====
export async function apiLogin({ studentId, password }) {
  const data = await apiJson("/public/auth/login", {
    method: "POST",
    body: JSON.stringify({ studentId, password }),
  });

  const accessToken = data?.data?.accessToken;
  const refreshToken = data?.data?.refreshToken;
  set_tokens(accessToken, refreshToken);

  return data;
}

export async function apiSignup({ studentId, password, phoneNumber }) {
  return apiJson("/public/auth/signup", {
    method: "POST",
    body: JSON.stringify({ studentId, password, phoneNumber }),
  });
}

export async function apiLogout() {
  const res = await apiJson("/auth/logout", { method: "POST" });
  clear_tokens();
  return res;
}

// ===== 공개 클럽 (guest / member / owner 공용) =====
export async function fetch_public_clubs() {
  const res = await apiJson("/public/clubs", { method: "GET" });
  return res.data || [];
}

export async function fetch_public_club(club_id) {
  const res = await apiJson(`/public/clubs/${club_id}`, { method: "GET" });
  return res.data;
}

// ===== 멤버 =====
export async function fetch_member_club_apply(club_id) {
  const res = await apiJson(`/member/clubs/${club_id}/apply`, {
    method: "GET",
  });
  return res.data;
}

export async function fetch_mypage_name() {
  const res = await apiJson("/member/mypage/name", { method: "GET" });
  return res.data;
}

export async function fetch_mypage_profile() {
  const res = await apiJson("/member/mypage/update", { method: "GET" });
  return res.data;
}

export async function update_mypage_phone(newPhoneNumber) {
  const res = await apiJson("/member/mypage/update/phone", {
    method: "PUT",
    body: JSON.stringify({ newPhoneNumber }),
  });
  return res.data;
}

export async function update_mypage_email(newEmail) {
  const res = await apiJson("/member/mypage/update/email", {
    method: "PUT",
    body: JSON.stringify({ newEmail }),
  });
  return res.data;
}

export async function fetch_my_applications() {
  const res = await apiJson("/member/mypage/applications", { method: "GET" });
  return res.data || [];
}

export async function fetch_application_result(club_id) {
  const res = await apiJson(`/member/mypage/applications/${club_id}/result`, {
    method: "GET",
  });
  return res.data;
}

export async function delete_application(club_id) {
  const res = await apiJson(`/member/mypage/application/${club_id}/delete`, {
    method: "POST",
  });
  return res;
}

export const delete_member_application = delete_application;

export async function fetch_application_for_update(club_id) {
  const res = await apiJson(`/member/mypage/applications/${club_id}/update`, {
    method: "GET",
  });
  return res.data;
}

export async function update_application(club_id, payload) {
  const res = await apiJson(`/member/mypage/applications/${club_id}/update`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
  return res;
}

export async function api_member_withdraw() {
  const res = await apiJson("/member/mypage/delete", { method: "POST" });
  clear_tokens();
  return res;
}

// ✅ 오너: 내가 관리하는 동아리 목록 조회
export async function fetch_owner_managed_clubs() {
  const res = await apiJson("/owner/club/managed-clubs", { method: "GET" });
  return res.data || [];
}

// ===== OWNER: 이미지 업로드 (배치 Presigned URL) =====
export async function owner_issue_upload_urls(files = []) {
  const payload_files = files.map((file) => ({
    fileName: file?.name || "file",
    contentType: file?.type || "application/octet-stream",
  }));

  const res = await apiJson("/owner/club/upload-urls", {
    method: "POST",
    body: JSON.stringify({ files: payload_files }),
  });

  return Array.isArray(res?.data) ? res.data : [];
}

export async function owner_put_presigned_url(preSignedUrl, file) {
  const putRes = await fetch(preSignedUrl, {
    method: "PUT",
    headers: {
      "Content-Type": file?.type || "application/octet-stream",
    },
    body: file,
  });

  if (!putRes.ok) {
    const err = new Error("이미지 업로드에 실패했습니다.");
    err.status = putRes.status;
    throw err;
  }
  return true;
}

export async function owner_upload_images(files = []) {
  if (!Array.isArray(files) || files.length === 0) return [];

  const issued = await owner_issue_upload_urls(files);

  if (issued.length !== files.length) {
    throw new Error("업로드 URL 개수가 파일 개수와 다릅니다.");
  }

  const uploaded_names = [];

  for (let i = 0; i < files.length; i++) {
    const file = files[i];
    const issued_item = issued[i];

    if (!issued_item?.preSignedUrl || !issued_item?.fileName) {
      throw new Error(`업로드 URL 응답이 올바르지 않습니다 (index ${i})`);
    }

    await owner_put_presigned_url(issued_item.preSignedUrl, file);
    uploaded_names.push(issued_item.fileName);
  }

  return uploaded_names;
}

// ===== OWNER: 동아리 등록 (JSON) =====
export async function owner_register_club(payload) {
  const res = await owner_fetch_json("/owner/club/register/club", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      uploadedImageFileNames: payload?.uploadedImageFileNames || [],
      name: payload?.name ?? "",
      title: payload?.title ?? "",
      president: payload?.president ?? "",
      contact: payload?.contact ?? "",
      recruitingEnd: payload?.recruitingEnd ?? null,
      clubRoom: payload?.clubRoom ?? "",
      description: payload?.description ?? "",
    }),
  });

  return res;
}

// ===== OWNER: 동아리 상세 조회 (GET) =====
export async function fetch_owner_club_detail(club_id) {
  const res = await owner_fetch_json(`/owner/club/${club_id}`, {
    method: "GET",
  });
  return res.data || null;
}

export const owner_get_club = fetch_owner_club_detail;

// ===== OWNER: 동아리 수정 (PUT) =====
export async function owner_update_club(club_id, payload) {
  const res = await owner_fetch_json(`/owner/club/${club_id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  return res.data || null;
}

// ===== OWNER: 모집 시작(토글) =====
export async function owner_start_recruitment(club_id) {
  return apiJson(`/owner/club/${club_id}/start-recruitment`, {
    method: "POST",
  });
}

export async function owner_close_recruitment(club_id) {
  return apiJson(`/owner/club/${club_id}/close-recruitment`, {
    method: "POST",
  });
}

// ===== OWNER: 지원자 목록 =====
export async function fetch_owner_applicants(club_id) {
  const res = await owner_fetch_json(`/owner/club/${club_id}/applicants`, {
    method: "GET",
  });
  return res.data || [];
}

// ===== OWNER: 지원서 질문(커스텀 질문) 조회/저장 =====
export async function fetch_owner_club_questions(club_id) {
  const res = await apiJson(`/owner/clubs/${club_id}/questions`, {
    method: "GET",
  });
  return res.data || [];
}

export async function owner_update_club_questions(club_id, questions = []) {
  return apiJson(`/owner/clubs/${club_id}/questions`, {
    method: "PUT",
    body: JSON.stringify(questions),
  });
}

// ===== OWNER: 지원자 상세 조회 =====
export async function fetch_owner_applicant_detail(club_id, club_member_id) {
  const res = await apiJson(
    `/owner/club/${club_id}/applicants/${club_member_id}`,
    { method: "GET" },
  );
  return res.data;
}

// ===== OWNER: 지원자 상태 변경 =====
export async function owner_update_applicant_status(
  club_id,
  club_member_id,
  new_status,
) {
  const res = await apiJson(
    `/owner/club/${club_id}/applicants/${club_member_id}/status`,
    {
      method: "PATCH",
      body: JSON.stringify({ newStatus: new_status }),
    },
  );
  return res;
}

// ===== OWNER: 지원자 엑셀 다운로드 =====
export async function owner_download_applicants_excel(club_id) {
  const res = await apiFetch(`/owner/club/${club_id}/applicants/excel`, {
    method: "GET",
  });

  const content_type = (res.headers.get("content-type") || "").toLowerCase();

  if (content_type.includes("application/json")) {
    const json = await res.json().catch(() => null);

    if (!res.ok || json?.status === "FAIL") {
      const err = new Error(json?.message || "엑셀 다운로드에 실패했습니다.");
      err.code =
        json?.errorCode || (res.status === 401 ? "UNAUTHORIZED" : "ERROR");
      err.status = res.status;
      err.raw = json;
      throw err;
    }

    const maybe_string = json?.data;

    if (typeof maybe_string === "string" && /^https?:\/\//.test(maybe_string)) {
      window.open(maybe_string, "_blank", "noopener,noreferrer");
      return true;
    }

    const blob = new Blob([maybe_string ?? ""], {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `applicants_${club_id}.xlsx`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
    return true;
  }

  if (!res.ok) {
    let msg = "엑셀 다운로드에 실패했습니다.";
    try {
      const txt = await res.text();
      if (txt) msg = txt;
    } catch {}
    const err = new Error(msg);
    err.status = res.status;
    throw err;
  }

  const blob = await res.blob();
  const url = URL.createObjectURL(blob);

  const cd = res.headers.get("content-disposition") || "";
  const match =
    /filename\*=utf-8''([^;]+)|filename="?([^"]+)"?/i.exec(cd) || [];
  const filename = decodeURIComponent(
    match[1] || match[2] || `applicants_${club_id}.xlsx`,
  );

  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);

  return true;
}

// ===== OWNER: 합불 결과 메일 발송 =====
export async function owner_send_result_email(club_id) {
  return apiJson(`/owner/club/email/${club_id}`, {
    method: "POST",
  });
}

// ===== MEMBER: 지원서 파일 업로드용 presigned url 발급 =====
export async function member_issue_application_upload_url(file) {
  const res = await apiJson("/member/clubs/application/upload-url", {
    method: "POST",
    body: JSON.stringify({
      originalFileName: file?.name || "file",
      contentType: file?.type || "application/octet-stream",
    }),
  });
  return res?.data || null;
}

export async function member_put_presigned_url(preSignedUrl, file) {
  const putRes = await fetch(preSignedUrl, {
    method: "PUT",
    headers: { "Content-Type": file?.type || "application/octet-stream" },
    body: file,
  });

  if (!putRes.ok) {
    const err = new Error("파일 업로드에 실패했습니다.");
    err.status = putRes.status;
    throw err;
  }
  return true;
}

// ===== MEMBER: 동아리 지원서 제출 =====
export async function member_apply_club(club_id, payload) {
  const res = await apiJson(`/member/clubs/${club_id}/apply`, {
    method: "POST",
    body: JSON.stringify(payload),
  });
  return res?.data || null;
}

// ===== MEMBER: (지원서 첨부파일) 다운로드 URL 발급 =====
export async function member_issue_application_download_url(file_key) {
  const q = encodeURIComponent(String(file_key || ""));
  const candidates = [
    `/member/mypage/applications/file-url?fileKey=${q}`,
    `/member/mypage/applications/file-url?file_key=${q}`,
    `/member/applications/file-url?fileKey=${q}`,
    `/files/presigned-download?fileKey=${q}`,
    `/file/presigned-download?fileKey=${q}`,
  ];

  let last_err = null;

  for (const path of candidates) {
    try {
      const data = await apiJson(path, { method: "GET" });
      return data;
    } catch (e) {
      last_err = e;
    }
  }

  throw last_err || new Error("download url api not found");
}

// ===== OWNER: (지원서 첨부파일) 다운로드 URL 발급 =====
export async function owner_issue_application_download_url(
  club_id,
  club_member_id,
  file_key,
) {
  const q = encodeURIComponent(String(file_key || ""));
  const candidates = [
    `/owner/club/${club_id}/applicants/${club_member_id}/file-url?fileKey=${q}`,
    `/owner/club/${club_id}/applicants/${club_member_id}/file-url?file_key=${q}`,
    `/owner/files/presigned-download?fileKey=${q}`,
    `/files/presigned-download?fileKey=${q}`,
  ];

  let last_err = null;

  for (const path of candidates) {
    try {
      const data = await apiJson(path, { method: "GET" });
      return data;
    } catch (e) {
      last_err = e;
    }
  }

  throw last_err || new Error("download url api not found");
}
