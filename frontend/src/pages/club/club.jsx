// src/pages/club/club.jsx (부분 교체: "지원하기" 노출 조건에 모집 시작 여부 추가)

import { useParams, useNavigate } from "react-router-dom";
import { useState, useRef, useEffect, useMemo } from "react";
import "../../styles/globals.css";
import "./club.css";
import {
  fetch_public_club,
  fetch_owner_club_detail,
  fetch_member_club_apply,
  is_logged_in,
  fetch_owner_managed_clubs,
  fetch_my_applications,
} from "../../lib/api";

function fmt_date(v) {
  if (!v) return "-";
  const s = String(v);
  if (s.includes("T")) return s.split("T")[0];
  return s;
}

function get_id(obj) {
  const v =
    obj?.clubId ??
    obj?.club_id ??
    obj?.id ??
    obj?.club?.clubId ??
    obj?.club?.id ??
    obj?.club?.club_id ??
    obj?.application?.clubId ??
    obj?.application?.club_id ??
    obj?.clubInfo?.clubId ??
    obj?.clubInfo?.id;
  return v === undefined || v === null ? null : String(v);
}

function as_time(v) {
  if (!v) return null;
  const t = Date.parse(String(v));
  return Number.isFinite(t) ? t : null;
}

export default function ClubPage() {
  const { id } = useParams();
  const nav = useNavigate();

  const [club, setClub] = useState(null);
  const [images, setImages] = useState([]);
  const [activeIndex, setActiveIndex] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error_msg, set_error_msg] = useState("");
  const carouselRef = useRef(null);

  const [owner_ids, set_owner_ids] = useState(new Set());
  const [applied_ids, set_applied_ids] = useState(new Set());

  const is_owner = useMemo(() => owner_ids.has(String(id)), [owner_ids, id]);
  const is_applied = useMemo(
    () => applied_ids.has(String(id)),
    [applied_ids, id],
  );

  const is_guest = useMemo(() => !is_logged_in(), []);

  // ✅ 모집 시작 여부 판단 (API가 어떤 키로 주는지 모르니 여러 케이스 대응)
  const is_recruitment_started = useMemo(() => {
    if (!club) return false;

    // 1) boolean 플래그가 있다면 그걸 최우선
    const flags = [
      club?.recruitingStarted,
      club?.isRecruitingStarted,
      club?.recruitStarted,
      club?.isRecruitStarted,
      club?.isRecruiting,
      club?.recruitingOpen,
    ];
    for (const f of flags) {
      if (typeof f === "boolean") return f;
    }

    // 2) 상태값이 있다면 OPEN일 때만 true
    const status = String(
      club?.recruitingStatus ?? club?.status ?? club?.recruitStatus ?? "",
    ).toUpperCase();
    if (status) {
      if (status === "OPEN") return true;
      if (status === "CLOSED") return false;
      if (status === "UPCOMING") return false;
      if (status === "PENDING") return false;
      if (status === "WAITING") return false;
    }

    // 3) 시작일이 있다면 now >= start 일 때만 true
    const start = as_time(
      club?.recruitingStart ??
        club?.recruitStart ??
        club?.recruitStartAt ??
        club?.recruitingStartAt,
    );
    if (start !== null) return Date.now() >= start;

    // 4) 아무 정보도 없으면 "시작 안함"으로 처리 (안전)
    return false;
  }, [club]);

  const can_show_apply = useMemo(() => {
    if (is_guest) return false;
    if (is_owner) return false;
    if (is_applied) return false;
    if (!is_recruitment_started) return false; // ✅ 추가
    return true;
  }, [is_guest, is_owner, is_applied, is_recruitment_started]);

  useEffect(() => {
    const load_owner_and_applied = async () => {
      if (!is_logged_in()) {
        set_owner_ids(new Set());
        set_applied_ids(new Set());
        return;
      }

      try {
        const [ownerData, appsData] = await Promise.allSettled([
          fetch_owner_managed_clubs(),
          fetch_my_applications(),
        ]);

        if (ownerData.status === "fulfilled") {
          const owners = Array.isArray(ownerData.value) ? ownerData.value : [];
          const next_owner = new Set(
            owners
              .map((c) => c?.clubId ?? c?.id ?? c?.club_id)
              .filter((v) => v !== undefined && v !== null)
              .map(String),
          );
          set_owner_ids(next_owner);
        } else {
          set_owner_ids(new Set());
        }

        if (appsData.status === "fulfilled") {
          const apps = Array.isArray(appsData.value) ? appsData.value : [];
          const next_applied = new Set(
            apps.map(get_id).filter((v) => v !== null),
          );
          set_applied_ids(next_applied);
        } else {
          set_applied_ids(new Set());
        }
      } catch {
        set_owner_ids(new Set());
        set_applied_ids(new Set());
      }
    };

    load_owner_and_applied();
  }, []);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      set_error_msg("");

      try {
        const logged_in = is_logged_in();

        const data =
          logged_in && is_owner
            ? await fetch_owner_club_detail(id)
            : await fetch_public_club(id);

        const club_data = data?.data ?? data;
        setClub(club_data);

        const club_images = Array.isArray(club_data?.clubImages)
          ? club_data.clubImages
          : [];

        const urls_from_club_images = club_images
          .slice()
          .sort((a, b) => (a?.orderNumber ?? 0) - (b?.orderNumber ?? 0))
          .map((it) => it?.imageUrl)
          .filter((v) => v && String(v).trim() && v !== "string");

        const thumb =
          club_data?.thumbnailUrl &&
          String(club_data.thumbnailUrl).trim() &&
          club_data.thumbnailUrl !== "string"
            ? club_data.thumbnailUrl
            : null;

        const final_urls =
          urls_from_club_images.length > 0
            ? urls_from_club_images
            : thumb
              ? [thumb]
              : [];

        setImages(final_urls);

        setActiveIndex(0);
        carouselRef.current?.scrollTo({ left: 0 });
      } catch (err) {
        set_error_msg(err.message || "동아리 정보를 불러오지 못했습니다.");
        setClub(null);
        setImages([]);
        setActiveIndex(0);
      } finally {
        setLoading(false);
      }
    };

    load();
  }, [id, is_owner]);

  const goTo = (nextIdx) => {
    if (!carouselRef.current || images.length === 0) return;
    const total = images.length;
    const clamped = (nextIdx + total) % total;
    setActiveIndex(clamped);

    const slideWidth = carouselRef.current.clientWidth;
    carouselRef.current.scrollTo({
      left: clamped * slideWidth,
      behavior: "smooth",
    });
  };

  const onPrev = () => goTo(activeIndex - 1);
  const onNext = () => goTo(activeIndex + 1);

  const onScroll = () => {
    if (!carouselRef.current) return;
    const slideWidth = carouselRef.current.clientWidth;
    const idx = Math.round(carouselRef.current.scrollLeft / slideWidth);
    if (idx !== activeIndex) setActiveIndex(idx);
  };

  const handleApply = async () => {
    if (!can_show_apply) return;

    try {
      const data = await fetch_member_club_apply(id);
      nav("/apply_form_submit", {
        state: {
          club,
          applyData: data,
        },
      });
    } catch (err) {
      set_error_msg(err.message || "지원 정보를 불러오지 못했습니다.");
    }
  };

  return (
    <div className="club_page">
      <header className="page-header sticky-header safe-area-top page-header--club">
        <div className="container">
          <div className="page-header-content">
            <button
              className="back-btn"
              aria-label="뒤로가기"
              onClick={() => nav(-1)}
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

            <h1>{club?.name || `클럽 ${id} 상세`}</h1>

            {can_show_apply && (
              <button className="apply_btn" onClick={handleApply}>
                지원하기
              </button>
            )}
          </div>
        </div>
      </header>

      <main className="club_main safe-area-padding">
        <div className="container">
          {error_msg && (
            <p
              aria-live="polite"
              style={{ minHeight: 20, color: "#a82d2f", marginBottom: "8px" }}
            >
              {error_msg}
            </p>
          )}

          {loading ? (
            <div className="club-loading">동아리 정보를 불러오는 중...</div>
          ) : !club ? (
            <div className="club-empty">동아리 정보를 찾을 수 없습니다.</div>
          ) : (
            <>
              <section className="gallery card">
                {images.length === 0 ? (
                  <div className="no_image">등록된 이미지가 없습니다.</div>
                ) : (
                  <>
                    <div
                      className="carousel"
                      ref={carouselRef}
                      onScroll={onScroll}
                    >
                      {images.map((src, i) => (
                        <div className="slide" key={i}>
                          <img src={src} alt={`클럽 이미지 ${i + 1}`} />
                        </div>
                      ))}
                    </div>

                    {images.length > 1 && (
                      <>
                        <button
                          className="nav prev"
                          onClick={onPrev}
                          aria-label="이전 이미지"
                        >
                          ‹
                        </button>
                        <button
                          className="nav next"
                          onClick={onNext}
                          aria-label="다음 이미지"
                        >
                          ›
                        </button>

                        <div className="dots">
                          {images.map((_, i) => (
                            <button
                              key={i}
                              className={i === activeIndex ? "is_active" : ""}
                              aria-label={`${i + 1}번째 이미지로 이동`}
                              onClick={() => goTo(i)}
                            />
                          ))}
                        </div>
                      </>
                    )}
                  </>
                )}
              </section>

              <section className="club_meta card">
                <ul className="info_list">
                  <li>
                    <span className="label">회장</span>
                    <span className="val">{club.president || "-"}</span>
                  </li>
                  <li>
                    <span className="label">연락처</span>
                    <span className="val">{club.contact || "-"}</span>
                  </li>
                  <li>
                    <span className="label">모집 마감</span>
                    <span className="val">{fmt_date(club.recruitingEnd)}</span>
                  </li>
                </ul>
              </section>

              <section className="intro card">
                <h2 className="section_title">동아리 소개</h2>

                {club?.description ? (
                  <div
                    className="desc rich_desc club_description"
                    dangerouslySetInnerHTML={{ __html: club.description }}
                  />
                ) : (
                  <p className="desc">
                    {`이곳에 클럽 ${id}의 소개글을 넣어주세요. 활동 목적, 주요 활동, 성과 등을 적을 수 있습니다.`}
                  </p>
                )}
              </section>
            </>
          )}
        </div>
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
