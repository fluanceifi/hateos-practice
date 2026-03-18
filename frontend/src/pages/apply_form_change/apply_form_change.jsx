// src/pages/apply_form_change/apply_form_change.jsx (전체 교체)
import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import "../../styles/globals.css";
import "./apply_form_change.css";
import {
  is_logged_in,
  fetch_application_for_update,
  update_application,
  delete_application,
  member_issue_application_upload_url,
  member_put_presigned_url,
  member_issue_application_download_url,
} from "../../lib/api";

function unwrap_api(res) {
  if (res && typeof res === "object") {
    if (res.data != null && typeof res.data === "object") return res.data;
    if (res.result != null && typeof res.result === "object") return res.result;
  }
  return res;
}

function normalize_content(s) {
  return String(s || "")
    .replace(/\s+/g, " ")
    .trim();
}

function is_file_question_content(content) {
  const t = normalize_content(content).toLowerCase();
  if (!t) return false;

  if (t === "파일추가") return true;

  if (t.includes("파일") && (t.includes("업로드") || t.includes("첨부")))
    return true;

  if (t.includes("포트폴리오") && (t.includes("업로드") || t.includes("첨부")))
    return true;

  if (t.includes("이력서") && (t.includes("업로드") || t.includes("첨부")))
    return true;

  if (t.includes("파일")) return true;

  return false;
}

function infer_input_type(question_content) {
  const t = normalize_content(question_content).toLowerCase();
  if (t.includes("성별")) return "gender";
  return "text";
}

function infer_gender_value(answer_content) {
  const v = normalize_content(answer_content);
  if (v === "남" || v === "남성" || v.toLowerCase() === "male") return "male";
  if (v === "여" || v === "여성" || v.toLowerCase() === "female")
    return "female";
  if (!v) return "";
  return "other";
}

function is_http_url(v) {
  const s = String(v || "").trim();
  return s.startsWith("http://") || s.startsWith("https://");
}

function try_decode_uri_component(s) {
  try {
    return decodeURIComponent(s);
  } catch {
    return s;
  }
}

function with_cache_bust(url) {
  if (!url) return "";
  const s = String(url);
  const t = Date.now();
  return s.includes("?") ? `${s}&_t=${t}` : `${s}?_t=${t}`;
}

// fileKey 형식이 "uuid_원본파일명(인코딩)" 같이 오는 케이스면 원본명 추정
function infer_original_name_from_key(file_key) {
  if (!file_key) return "";
  const s = String(file_key);

  if (is_http_url(s)) {
    try {
      const u = new URL(s);
      const pathname = u.pathname || "";
      const last = pathname.split("/").filter(Boolean).pop() || "";
      return try_decode_uri_component(last);
    } catch {
      return "";
    }
  }

  const last = s.split("/").filter(Boolean).pop() || "";
  const idx = last.lastIndexOf("_");
  if (idx >= 0 && idx < last.length - 1) {
    const maybe = last.slice(idx + 1);
    const decoded = try_decode_uri_component(maybe);
    if (decoded) return decoded;
  }
  return try_decode_uri_component(last);
}

function pick_filename_from_content_disposition(cd) {
  if (!cd) return "";

  const s = String(cd);

  const m1 = s.match(/filename\*\s*=\s*utf-8''([^;]+)/i);
  if (m1 && m1[1]) {
    return try_decode_uri_component(m1[1].trim().replace(/(^"|"$)/g, ""));
  }

  const m2 = s.match(/filename\s*=\s*("?)([^";]+)\1/i);
  if (m2 && m2[2]) {
    return try_decode_uri_component(m2[2].trim().replace(/(^"|"$)/g, ""));
  }

  return "";
}

export default function ApplyFormChange() {
  const navigate = useNavigate();
  const { id } = useParams();
  const club_id = useMemo(() => (id == null ? null : String(id)), [id]);

  const [loading, set_loading] = useState(true);
  const [saving, set_saving] = useState(false);
  const [error_msg, set_error_msg] = useState("");

  const [member_info, set_member_info] = useState({
    memberId: "",
    studentId: "",
    name: "",
    phone: "",
    department: "",
  });

  const [questions, set_questions] = useState([]);
  const [has_file_upload, set_has_file_upload] = useState(false);

  // 백엔드에서 fileKeyUrl로 내려오는 경우가 있어 통합 관리
  const [file_key, set_file_key] = useState(""); // (key or url)
  const [file_display_name, set_file_display_name] = useState(""); // 화면 표시용(원본명)

  const [file_url, set_file_url] = useState("");
  const [file_url_loading, set_file_url_loading] = useState(false);

  const file_input_ref = useRef(null);
  const latest_file_key_ref = useRef(""); // ✅ 추가

  const load = async () => {
    if (!club_id) {
      set_error_msg("club id가 없습니다.");
      set_loading(false);
      return;
    }

    set_loading(true);
    set_error_msg("");

    try {
      const raw = await fetch_application_for_update(club_id);
      const data = unwrap_api(raw);

      console.log("[apply change] full data:", data);

      set_member_info({
        memberId: data?.memberId ?? "",
        studentId: data?.studentId ?? "",
        name: data?.name ?? "",
        phone: data?.phone ?? "",
        department: data?.department ?? "",
      });

      // ✅ 스웨거 응답처럼 fileKeyUrl로 내려오는 케이스 대응
      const server_file_key =
        data?.fileKeyUrl ??
        data?.file_key_url ??
        data?.fileKey ??
        data?.file_key ??
        "";

      const next_key = server_file_key ? String(server_file_key) : "";
      set_file_key(next_key);
      latest_file_key_ref.current = next_key; // ✅ 추가
      set_file_display_name(infer_original_name_from_key(next_key));

      const raw_qna =
        data?.questionAndAnswer ??
        data?.question_and_answer ??
        data?.questions ??
        data?.qna ??
        [];

      const list = (Array.isArray(raw_qna) ? raw_qna : [])
        .map((q) => ({
          questionId: q?.questionId ?? q?.id ?? null,
          orderNum:
            typeof q?.orderNum === "number"
              ? q.orderNum
              : typeof q?.orderNumber === "number"
                ? q.orderNumber
                : 0,
          questionContent: q?.questionContent ?? q?.content ?? "",
          answerContent: q?.answerContent ?? q?.answer ?? "",
        }))
        .filter((q) => q.questionId != null)
        .sort((a, b) => (a.orderNum ?? 0) - (b.orderNum ?? 0))
        .map((q) => {
          const qc = normalize_content(q.questionContent);
          const is_file = is_file_question_content(qc);
          const type = is_file ? "file" : infer_input_type(qc);
          return { ...q, questionContent: qc, type };
        });

      const file_item = list.find((q) => q.type === "file");

      // ✅ file 질문이 없어도, 서버에서 fileKeyUrl/fileKey가 내려오면 파일 섹션 보이게
      const has_server_file = !!server_file_key;
      set_has_file_upload(!!file_item || has_server_file);

      // ✅ 서버 필드가 비어있고, file 질문의 answerContent에 키/URL이 들어오는 케이스 보정
      if (!server_file_key && file_item?.answerContent) {
        const k = String(file_item.answerContent);
        set_file_key(k);
        latest_file_key_ref.current = k; // ✅ 추가

        set_file_display_name(infer_original_name_from_key(k));
      }

      set_questions(list.filter((q) => q.type !== "file"));
    } catch (e) {
      set_error_msg(e?.message || "지원서 정보를 불러오지 못했습니다.");
      set_member_info({
        department: "",
        memberId: "",
        studentId: "",
        name: "",
        phone: "",
      });
      set_questions([]);
      set_has_file_upload(false);
      set_file_key("");
      set_file_display_name("");
      set_file_url("");
    } finally {
      set_loading(false);
    }
  };

  useEffect(() => {
    if (!is_logged_in()) {
      navigate("/login");
      return;
    }
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [club_id, navigate]);

  useEffect(() => {
    const run = async () => {
      if (!has_file_upload) {
        set_file_url("");
        return;
      }
      if (!file_key) {
        set_file_url("");
        return;
      }

      // ✅ file_key가 이미 URL이면 그대로 사용 (캐시 방지)
      if (is_http_url(file_key)) {
        set_file_url(with_cache_bust(String(file_key)));
        return;
      }

      set_file_url_loading(true);
      try {
        // ✅ file_key(오브젝트 키)로 다운로드 URL 발급
        const raw = await member_issue_application_download_url(file_key);
        const data = unwrap_api(raw);

        const url =
          data?.preSignedUrl ??
          data?.presignedUrl ??
          data?.url ??
          data?.downloadUrl ??
          raw?.preSignedUrl ??
          raw?.presignedUrl ??
          raw?.url ??
          raw?.downloadUrl ??
          "";

        set_file_url(url ? with_cache_bust(String(url)) : "");
      } catch {
        set_file_url("");
      } finally {
        set_file_url_loading(false);
      }
    };

    run();
  }, [file_key, has_file_upload]);

  const update_answer = (question_id, next_value) => {
    set_questions((prev) =>
      (prev || []).map((q) =>
        String(q.questionId) === String(question_id)
          ? { ...q, answerContent: next_value }
          : q,
      ),
    );
  };

  const on_pick_file = () => file_input_ref.current?.click();

  const on_file_change = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;

    try {
      set_saving(true);

      const issued_raw = await member_issue_application_upload_url(file);
      const issued = unwrap_api(issued_raw);

      const preSignedUrl = issued?.preSignedUrl ?? issued?.presignedUrl ?? "";
      const fileName = issued?.fileName ?? issued?.filename ?? ""; // 서버가 내려주는 key/이름

      if (!preSignedUrl || !fileName) {
        throw new Error("업로드 URL 발급 응답이 올바르지 않습니다.");
      }

      await member_put_presigned_url(preSignedUrl, file);

      // ✅ 캐시된 이전 다운로드 URL 끊어주기
      set_file_url("");

      // ✅ 업로드 후 file_key 갱신 (다운로드 버튼 즉시 활성화)
      set_file_key(String(fileName));
      latest_file_key_ref.current = String(fileName); // ✅ 추가

      // ✅ 화면에는 원본 파일명 우선 표시
      set_file_display_name(
        file?.name ? String(file.name) : infer_original_name_from_key(fileName),
      );

      alert("파일이 업로드되었습니다.");
    } catch (err) {
      alert(err?.message || "파일 업로드에 실패했습니다.");
    } finally {
      set_saving(false);
    }
  };

  const on_save = async () => {
    if (!club_id) return;
    if (saving) return;

    set_saving(true);
    set_error_msg("");

    try {
      const answers = (questions || [])
        .filter((q) => q?.questionId != null)
        .map((q) => ({
          questionId: Number(q.questionId) || q.questionId,
          answerContent: String(q.answerContent ?? ""),
        }));

      // ✅ 여기부터 추가/수정
      console.log("[on_save] state file_key:", file_key);
      console.log("[on_save] ref file_key:", latest_file_key_ref.current);

      const final_file_key = has_file_upload
        ? String(latest_file_key_ref.current || "")
        : "";

      const payload = {
        answers,
        fileKey: final_file_key,
      };
      // ✅ 여기까지

      await update_application(club_id, payload);

      alert("수정이 완료되었습니다.");
      await load();
    } catch (e) {
      set_error_msg(e?.message || "저장에 실패했습니다.");
    } finally {
      set_saving(false);
    }
  };

  const on_delete = async () => {
    if (!club_id) return;
    if (saving) return;

    const ok = window.confirm("정말 지원을 취소(삭제)하시겠습니까?");
    if (!ok) return;

    try {
      set_saving(true);
      await delete_application(club_id);
      alert("지원이 취소되었습니다.");
      navigate("/mypage");
    } catch (e) {
      alert(e?.message || "지원 취소에 실패했습니다.");
    } finally {
      set_saving(false);
    }
  };

  // ✅ 강제 다운로드 (가능하면 blob), 실패 시 새창 열기 fallback
  const on_download = async () => {
    if (!file_url) return;

    const open_url = with_cache_bust(file_url);

    const fallback_open = () => {
      window.open(open_url, "_blank", "noopener,noreferrer");
    };

    try {
      const res = await fetch(open_url, { method: "GET" });
      if (!res.ok) throw new Error("download failed");

      const cd = res.headers.get("content-disposition") || "";
      const from_cd = pick_filename_from_content_disposition(cd);
      const filename =
        from_cd ||
        file_display_name ||
        infer_original_name_from_key(file_key) ||
        "download";

      const blob = await res.blob();
      const object_url = URL.createObjectURL(blob);

      const a = document.createElement("a");
      a.href = object_url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);

      URL.revokeObjectURL(object_url);
    } catch {
      fallback_open();
    }
  };

  if (loading) {
    return (
      <div className="page-root">
        <div className="page-header sticky-header safe-area-top">
          <div className="container">
            <div className="page-header-content">
              <button
                type="button"
                className="back-btn"
                aria-label="뒤로가기"
                onClick={() => navigate(-1)}
              >
                <svg
                  className="icon"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                >
                  <path d="M19 12H5" />
                  <path d="M12 19l-7-7 7-7" />
                </svg>
              </button>
              <h1>지원서 수정</h1>
            </div>
          </div>
        </div>

        <main className="page-main apply_main">
          <section className="apply_section">
            <h2 className="apply_title">지원서 수정</h2>
            <div className="apply_card">불러오는 중...</div>
          </section>
        </main>
      </div>
    );
  }

  return (
    <div className="page-root">
      <div className="page-header sticky-header safe-area-top">
        <div className="container">
          <div className="page-header-content">
            <button
              type="button"
              className="back-btn"
              aria-label="뒤로가기"
              onClick={() => navigate(-1)}
            >
              <svg
                className="icon"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
              >
                <path d="M19 12H5" />
                <path d="M12 19l-7-7 7-7" />
              </svg>
            </button>
            <h1>지원서 수정</h1>
          </div>
        </div>
      </div>

      <main className="page-main apply_main">
        <section className="apply_section">
          <h2 className="apply_title">지원서 수정</h2>

          <div className="apply_card">
            {error_msg && <p className="mypage_error">{error_msg}</p>}

            <p className="desc">
              제출한 지원서를 수정하거나, 지원을 취소할 수 있습니다.{" "}
              {saving ? (
                <span style={{ opacity: 0.7 }}>(처리중...)</span>
              ) : null}
            </p>

            <label className="field_label">학과</label>
            <input
              className="field_input"
              value={member_info.department || ""}
              disabled
              readOnly
            />
            <label className="field_label">학번</label>
            <input
              className="field_input"
              value={member_info.studentId || ""}
              disabled
              readOnly
            />

            <label className="field_label">이름</label>
            <input
              className="field_input"
              value={member_info.name || ""}
              disabled
              readOnly
            />

            <label className="field_label">전화번호</label>
            <input
              className="field_input"
              value={member_info.phone || ""}
              disabled
              readOnly
            />

            {(questions || []).length > 0 ? (
              <div className="custom_list">
                {questions
                  .slice()
                  .sort((a, b) => (a.orderNum ?? 0) - (b.orderNum ?? 0))
                  .map((q, idx) => {
                    const is_gender = q.type === "gender";
                    const gender_value = infer_gender_value(q.answerContent);

                    return (
                      <div key={q.questionId} className="q_item">
                        <label className="field_label">
                          {idx + 1}. {q.questionContent || "질문"}
                        </label>

                        {is_gender ? (
                          <fieldset className="fieldset">
                            <legend
                              className="field_label"
                              style={{ display: "none" }}
                            >
                              성별
                            </legend>

                            <label className="radio_item">
                              <input
                                type="radio"
                                name={`gender-${q.questionId}`}
                                value="male"
                                checked={gender_value === "male"}
                                onChange={() =>
                                  update_answer(q.questionId, "남성")
                                }
                                disabled={saving}
                              />
                              남성
                            </label>

                            <label className="radio_item">
                              <input
                                type="radio"
                                name={`gender-${q.questionId}`}
                                value="female"
                                checked={gender_value === "female"}
                                onChange={() =>
                                  update_answer(q.questionId, "여성")
                                }
                                disabled={saving}
                              />
                              여성
                            </label>

                            <label className="radio_item">
                              <input
                                type="radio"
                                name={`gender-${q.questionId}`}
                                value="other"
                                checked={gender_value === "other"}
                                onChange={() =>
                                  update_answer(q.questionId, "기타")
                                }
                                disabled={saving}
                              />
                              기타
                            </label>
                          </fieldset>
                        ) : (
                          <textarea
                            className="answer_area"
                            placeholder="답변을 입력하세요"
                            value={q.answerContent ?? ""}
                            onChange={(e) =>
                              update_answer(q.questionId, e.target.value)
                            }
                            disabled={saving}
                          />
                        )}
                      </div>
                    );
                  })}
              </div>
            ) : (
              <p className="desc" style={{ opacity: 0.8 }}>
                수정 가능한 질문이 없습니다.
              </p>
            )}

            {has_file_upload && (
              <div className="file_upload_section">
                <label className="field_label">첨부파일</label>

                <div className="file_row">
                  <span className="file_name">
                    {file_key
                      ? file_display_name ||
                        infer_original_name_from_key(file_key) ||
                        "첨부 있음"
                      : "첨부 없음"}
                    {file_url_loading ? (
                      <span style={{ marginLeft: 8, opacity: 0.7 }}>
                        (링크 생성중...)
                      </span>
                    ) : null}
                  </span>

                  <input
                    ref={file_input_ref}
                    type="file"
                    style={{ display: "none" }}
                    onChange={on_file_change}
                  />

                  <button
                    type="button"
                    className="outline_btn sm"
                    onClick={on_download}
                    disabled={!file_url || saving}
                    style={!file_url ? { opacity: 0.5 } : undefined}
                  >
                    열기/다운로드
                  </button>

                  <button
                    type="button"
                    className="outline_btn sm"
                    onClick={on_pick_file}
                    disabled={saving}
                  >
                    파일 변경
                  </button>
                </div>
              </div>
            )}

            <div className="form_actions">
              <button
                type="button"
                className="delete_btn"
                onClick={on_delete}
                disabled={saving}
                style={{ borderColor: "#a82d2f", color: "#a82d2f" }}
              >
                지원 취소(삭제)
              </button>

              <button
                type="button"
                className="primary_btn save_btn"
                onClick={on_save}
                disabled={saving}
              >
                {saving ? "저장중..." : "수정 저장"}
              </button>
            </div>
          </div>
        </section>
      </main>

      <div className="page-footer">
        <p>© 2025 smu-club. 상명대학교 동아리 플랫폼</p>
        <p>
          <a
            href="https://github.com/smu-human/smu-club"
            target="_blank"
            rel="noopener noreferrer"
          >
            Github
          </a>
        </p>
      </div>
    </div>
  );
}
