// src/pages/club_manage/club_manage.jsx
import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import "../../styles/globals.css";
import "./club_manage.css";
import { Editor } from "@toast-ui/react-editor";
import "@toast-ui/editor/dist/toastui-editor.css";

import {
  fetch_owner_club_detail,
  owner_update_club,
  owner_upload_images,
} from "../../lib/api";

function extract_object_key(v) {
  const s = String(v || "").trim();
  if (!s) return null;

  if (!s.startsWith("http")) return s;

  const idx = s.indexOf("/o/");
  if (idx >= 0) return s.slice(idx + 3);

  return s;
}

function normalize_existing_images(detail) {
  const club_images = Array.isArray(detail?.clubImages)
    ? detail.clubImages
    : [];

  const keys = club_images
    .slice()
    .sort((a, b) => (a?.orderNumber ?? 0) - (b?.orderNumber ?? 0))
    .map((it) => extract_object_key(it?.imageUrl))
    .filter((v) => v && String(v).trim() && v !== "string");

  if (keys.length > 0) return keys;

  const fallback = Array.isArray(detail?.uploadedImageFileNames)
    ? detail.uploadedImageFileNames
    : [];

  return fallback
    .map((it) => extract_object_key(it))
    .filter((v) => v && String(v).trim() && v !== "string");
}

function to_display_name(v) {
  const s = String(v || "");
  try {
    const decoded = decodeURIComponent(s);
    const last = decoded.split("/").pop();
    return last || decoded;
  } catch {
    const last = s.split("/").pop();
    return last || s;
  }
}

export default function ClubManage() {
  const navigate = useNavigate();
  const { clubId } = useParams();
  const editorRef = useRef(null);

  const today_str = new Date().toISOString().slice(0, 10);

  const [club_name, set_club_name] = useState("");
  const [club_one_line, set_club_one_line] = useState("");
  const [leader_name, set_leader_name] = useState("");
  const [phone, set_phone] = useState("");

  const [deadline, set_deadline] = useState("");

  const [club_room, set_club_room] = useState("");

  const [existing_images, set_existing_images] = useState([]); // object key string[]
  const [new_images, set_new_images] = useState([]); // File[]
  const [is_loading, set_is_loading] = useState(true);
  const [is_saving, set_is_saving] = useState(false);

  // ✅ 실시간 “실제 렌더” 프리뷰
  const [preview_html, set_preview_html] = useState("");
  const [show_live_preview, set_show_live_preview] = useState(true);

  // ✅ 풀스크린(모달 느낌)
  const [is_editor_fullscreen, set_is_editor_fullscreen] = useState(false);

  const preview_timer_ref = useRef(null);
  const sync_preview_from_editor = () => {
    if (preview_timer_ref.current) clearTimeout(preview_timer_ref.current);

    preview_timer_ref.current = setTimeout(() => {
      const html = editorRef.current?.getInstance().getHTML() || "";
      set_preview_html(html);
    }, 200);
  };

  useEffect(() => {
    const on_key = (e) => {
      if (e.key === "Escape") set_is_editor_fullscreen(false);
    };
    window.addEventListener("keydown", on_key);
    return () => window.removeEventListener("keydown", on_key);
  }, []);

  const get_detail = async () => {
    const detail = await fetch_owner_club_detail(clubId);

    set_club_name(detail?.name ?? "");
    set_club_one_line(detail?.title ?? "");
    set_leader_name(detail?.president ?? "");
    set_phone(detail?.contact ?? "");

    set_deadline((detail?.recruitingEnd ?? "").slice(0, 10));

    set_club_room(detail?.clubRoom ?? "");

    set_existing_images(normalize_existing_images(detail));

    const html = detail?.description ?? "";
    const inst = editorRef.current?.getInstance();
    if (inst) inst.setHTML(html || "");

    set_preview_html(html || "");
  };

  useEffect(() => {
    if (!clubId) {
      navigate("/mypage");
      return;
    }

    const load = async () => {
      try {
        await get_detail();
      } catch (e) {
        alert(e?.message || "동아리 정보를 불러오지 못했습니다.");
        navigate("/mypage");
      } finally {
        set_is_loading(false);
      }
    };

    load();

    return () => {
      if (preview_timer_ref.current) clearTimeout(preview_timer_ref.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clubId]);

  const on_pick_new_images = (e) => {
    const files = Array.from(e.target.files || []);
    const remaining = 5 - existing_images.length;

    if (remaining <= 0) {
      alert("이미지는 최대 5장까지 등록할 수 있습니다.");
      e.target.value = "";
      return;
    }

    if (files.length > remaining) {
      alert(`이미지는 최대 ${remaining}장까지만 추가할 수 있습니다.`);
      set_new_images(files.slice(0, remaining));
    } else {
      set_new_images(files);
    }

    e.target.value = "";
  };

  const remove_existing_image = (idx) => {
    set_existing_images((prev) => prev.filter((_, i) => i !== idx));
  };

  const on_save = async () => {
    if (is_saving) return;
    set_is_saving(true);

    try {
      if (!deadline) {
        alert("모집 마감일을 설정해주세요.");
        return;
      }

      const intro_html = editorRef.current?.getInstance().getHTML() || "";

      const uploaded_new = new_images.length
        ? await owner_upload_images(new_images)
        : [];

      const uploaded_new_keys = (uploaded_new || [])
        .map((it) => extract_object_key(it))
        .filter(Boolean);

      const merged_images = [
        ...existing_images.map((it) => extract_object_key(it)).filter(Boolean),
        ...uploaded_new_keys,
      ];

      if (merged_images.length > 5) {
        alert("이미지는 최대 5장까지만 저장할 수 있습니다.");
        return;
      }

      await owner_update_club(clubId, {
        uploadedImageFileNames: merged_images,
        name: club_name,
        title: club_one_line,
        president: leader_name,
        contact: phone,
        recruitingEnd: deadline || null,
        clubRoom: club_room,
        description: intro_html,
      });

      alert("수정 완료");
      navigate("/mypage");
    } catch (e) {
      alert(e?.message || "수정 실패");
    } finally {
      set_is_saving(false);
    }
  };

  if (is_loading) {
    return (
      <div className="page-root">
        <div className="page-main club_manage_main">
          <p>불러오는 중...</p>
        </div>
      </div>
    );
  }

  const remaining_count = Math.max(0, 5 - existing_images.length);

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
            <h1>동아리 수정</h1>
          </div>
        </div>
      </div>

      <main className="page-main club_manage_main">
        <section className="club_section">
          <h2 className="club_title">갤러리 이미지</h2>
          <div className="club_card">
            <p className="sub_text">
              기존 이미지는 삭제할 수 있고, 새 이미지를 추가할 수 있어요. (최대
              5장)
            </p>

            <p className="hint_text">{`현재 ${existing_images.length}장 / 최대 5장`}</p>

            {existing_images.length > 0 && (
              <div className="image_list">
                {existing_images.map((img, idx) => (
                  <div className="image_item" key={`${img}-${idx}`}>
                    <span className="image_name">{to_display_name(img)}</span>
                    <button
                      type="button"
                      className="image_remove_btn"
                      onClick={() => remove_existing_image(idx)}
                    >
                      삭제
                    </button>
                  </div>
                ))}
              </div>
            )}

            <label
              className="outline_btn"
              htmlFor="clubGalleryNew"
              style={{ opacity: remaining_count === 0 ? 0.5 : 1 }}
            >
              이미지 추가
            </label>
            <input
              id="clubGalleryNew"
              type="file"
              accept="image/png, image/jpeg"
              multiple
              onChange={on_pick_new_images}
              disabled={remaining_count === 0}
              style={{ display: "none" }}
            />

            {new_images.length > 0 && (
              <p className="hint_text">
                {`추가 선택: ${new_images.length}개 (최대 ${remaining_count}개 가능)`}
              </p>
            )}
          </div>
        </section>

        <section className="club_section">
          <h2 className="club_title">기본 정보</h2>
          <div className="club_card">
            <label className="field_label" htmlFor="clubName">
              동아리명
            </label>
            <input
              id="clubName"
              className="field_input"
              type="text"
              value={club_name}
              onChange={(e) => set_club_name(e.target.value)}
            />

            <label className="field_label" htmlFor="clubOneLine">
              동아리 한줄 소개
            </label>
            <input
              id="clubOneLine"
              className="field_input"
              type="text"
              value={club_one_line}
              onChange={(e) => set_club_one_line(e.target.value)}
            />

            <label className="field_label" htmlFor="leaderName">
              회장
            </label>
            <input
              id="leaderName"
              className="field_input"
              type="text"
              value={leader_name}
              onChange={(e) => set_leader_name(e.target.value)}
            />

            <label className="field_label" htmlFor="phone">
              연락처
            </label>
            <input
              id="phone"
              className="field_input"
              type="tel"
              value={phone}
              onChange={(e) => set_phone(e.target.value)}
            />

            <label className="field_label" htmlFor="deadline">
              모집 마감일
            </label>
            <input
              id="deadline"
              className="field_input"
              type="date"
              value={deadline}
              min={today_str}
              onChange={(e) => set_deadline(e.target.value)}
            />

            {/* <label className="field_label" htmlFor="clubRoom">
              동아리방
            </label>
            <input
              id="clubRoom"
              className="field_input"
              type="text"
              value={club_room}
              onChange={(e) => set_club_room(e.target.value)}
            /> */}
          </div>
        </section>

        <section className="club_section">
          <h2 className="club_title">동아리 소개</h2>
          <div
            className={[
              "club_card",
              "editor_shell",
              is_editor_fullscreen ? "editor_fullscreen" : "",
            ].join(" ")}
          >
            <div className="editor_topbar">
              <div className="editor_topbar_left">
                <button
                  type="button"
                  className="mini_btn"
                  onClick={() => set_show_live_preview((v) => !v)}
                >
                  {show_live_preview ? "프리뷰 숨기기" : "프리뷰 보기"}
                </button>
              </div>

              <div className="editor_topbar_right">
                <button
                  type="button"
                  className="mini_btn"
                  onClick={() => {
                    set_is_editor_fullscreen((v) => !v);
                    setTimeout(() => {
                      const html =
                        editorRef.current?.getInstance().getHTML() || "";
                      set_preview_html(html);
                    }, 0);
                  }}
                >
                  {is_editor_fullscreen ? "전체화면 닫기 (ESC)" : "전체화면"}
                </button>
              </div>
            </div>

            <div
              className={[
                "editor_grid",
                show_live_preview ? "with_preview" : "no_preview",
              ].join(" ")}
            >
              <div className="editor_col">
                <Editor
                  ref={editorRef}
                  height={is_editor_fullscreen ? "74vh" : "520px"}
                  initialEditType="wysiwyg"
                  previewStyle="tab"
                  usageStatistics={false}
                  placeholder="동아리 소개와 활동 내용을 자유롭게 작성하세요."
                  onChange={sync_preview_from_editor}
                />
              </div>

              {show_live_preview && (
                <div className="preview_col">
                  <div className="preview_title">실제 화면 미리보기</div>

                  <div className="club_description preview_box">
                    <div
                      className="desc rich_desc"
                      dangerouslySetInnerHTML={{
                        __html: preview_html || "<p></p>",
                      }}
                    />
                  </div>
                </div>
              )}
            </div>
          </div>
        </section>

        <button
          className="primary_btn club_save_btn"
          onClick={on_save}
          disabled={is_saving}
        >
          {is_saving ? "저장 중..." : "수정 저장"}
        </button>
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
