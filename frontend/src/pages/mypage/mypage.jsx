// src/pages/mypage/mypage.jsx
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import "../../styles/globals.css";
import "./mypage.css";
import Result from "../../components/result";

import {
  fetch_mypage_name,
  fetch_my_applications,
  fetch_owner_managed_clubs,
  fetch_owner_club_detail,
  is_logged_in,
  apiLogout,
  api_member_withdraw,
  owner_start_recruitment,
  owner_close_recruitment,
  fetch_application_result,
  fetch_owner_club_questions,
} from "../../lib/api";

const HIDDEN_CLUBS_KEY = "smu_hidden_club_ids_v1";

export default function MyPage() {
  const navigate = useNavigate();

  const [name, setName] = useState("");
  const [applications, setApplications] = useState([]);
  const [managed_clubs, set_managed_clubs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error_msg, set_error_msg] = useState("");

  const [result_open, set_result_open] = useState(false);
  const [result_loading, set_result_loading] = useState(false);
  const [result_error, set_result_error] = useState("");
  const [result_data, set_result_data] = useState(null);

  // { [clubId]: "start" | "close" | null }
  const [recruiting_loading_map, set_recruiting_loading_map] = useState({});
  const [is_owner, set_is_owner] = useState(false);
  // ✅ 목록 API가 상태를 안 내려줘도 UI용 상태를 기억
  const [local_recruit_status, set_local_recruit_status] = useState({}); // { [id]: "OPEN" | "CLOSED" }

  const [hidden_ids, set_hidden_ids] = useState(() => {
    try {
      const raw = localStorage.getItem(HIDDEN_CLUBS_KEY);
      const arr = raw ? JSON.parse(raw) : [];
      return new Set(Array.isArray(arr) ? arr.map(String) : []);
    } catch {
      return new Set();
    }
  });

  const persist_hidden = (nextSet) => {
    try {
      localStorage.setItem(HIDDEN_CLUBS_KEY, JSON.stringify([...nextSet]));
    } catch {}
  };

  const hide_club = (club_id) => {
    const id = String(club_id);
    const next = new Set(hidden_ids);
    next.add(id);
    set_hidden_ids(next);
    persist_hidden(next);
  };

  const unhide_all = () => {
    const next = new Set();
    set_hidden_ids(next);
    persist_hidden(next);
  };

  const get_id = (obj) => {
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
  };

  const get_application_club_id = (app) => {
    const v =
      app?.clubId ??
      app?.club_id ??
      app?.club?.clubId ??
      app?.club?.id ??
      app?.club?.club_id ??
      app?.clubInfo?.clubId ??
      app?.clubInfo?.id ??
      app?.application?.clubId ??
      app?.application?.club_id ??
      app?.club_id_fk;
    return v === undefined || v === null ? null : String(v);
  };

  const get_name = (obj) =>
    obj?.clubName ??
    obj?.name ??
    obj?.club?.clubName ??
    obj?.club?.name ??
    obj?.clubInfo?.clubName ??
    obj?.clubInfo?.name ??
    "동아리";

  const is_application_item = (a) => {
    if (a?.applicationId != null) return true;
    if (a?.applyId != null) return true;
    if (a?.memberId != null) return true;
    if (a?.status != null) return true;
    if (a?.applicationStatus != null) return true;
    if (a?.applyStatus != null) return true;
    if (a?.appliedAt != null) return true;

    if (a?.president != null) return false;
    if (a?.contact != null) return false;
    if (a?.recruitingEnd != null) return false;
    if (a?.clubRoom != null) return false;
    if (a?.description != null) return false;

    return true;
  };

  const reload_owner_clubs = async () => {
    const ownerData = await fetch_owner_managed_clubs();
    const owners = Array.isArray(ownerData) ? ownerData : [];
    set_managed_clubs(owners);
    set_is_owner(true);
    return owners;
  };

  const has_questions = async (club_id) => {
    const qs = await fetch_owner_club_questions(club_id);
    return Array.isArray(qs) && qs.length > 0;
  };

  const get_recruitment_status = (club) => {
    const id = get_id(club);
    const local = id ? local_recruit_status[id] : null;
    if (local) return String(local).trim().toUpperCase();

    const raw =
      club?.recruitmentStatus ??
      club?.recruitingStatus ??
      club?.recruitStatus ??
      club?.status ??
      club?.recruitment_status ??
      club?.recruiting_status ??
      null;

    const s = String(raw || "")
      .trim()
      .toUpperCase();
    return s || null;
  };

  const is_deadline_passed = (club) => {
    const endRaw = club?.recruitingEnd ?? club?.recruiting_end ?? null;
    if (!endRaw) return false;

    const endDate = new Date(String(endRaw));
    if (Number.isNaN(endDate.getTime())) return false;

    const now = new Date();
    return now.getTime() >= endDate.getTime();
  };

  const is_recruiting_started = (club) => {
    const id = get_id(club);
    if (id && recruiting_loading_map[id] === "start") return true;

    const status = get_recruitment_status(club);
    if (!status) return false;

    return (
      status === "OPEN" ||
      status === "RECRUITING" ||
      status === "ONGOING" ||
      status === "IN_PROGRESS"
    );
  };

  const is_recruiting_closed = (club) => {
    const status = get_recruitment_status(club);
    if (is_deadline_passed(club)) return true;
    if (!status) return false;
    return (
      status === "CLOSED" ||
      status === "CLOSE" ||
      status === "ENDED" ||
      status === "END" ||
      status === "FINISHED"
    );
  };

  const ensure_deadline_ok_for_start = async (club_id, club_fallback) => {
    let detail = club_fallback;
    try {
      detail = await fetch_owner_club_detail(club_id);
    } catch {}

    const end_raw =
      detail?.recruitingEnd ??
      detail?.recruiting_end ??
      detail?.recruitEnd ??
      detail?.recruit_end ??
      null;

    if (
      !end_raw ||
      String(end_raw).trim() === "" ||
      String(end_raw).toLowerCase() === "null"
    ) {
      return { ok: false, reason: "NO_DEADLINE" };
    }

    const end_date = new Date(String(end_raw));
    if (!Number.isNaN(end_date.getTime())) {
      const now = new Date();
      if (now.getTime() >= end_date.getTime()) {
        return { ok: false, reason: "DEADLINE_PASSED" };
      }
    }

    return { ok: true };
  };

  const patch_status_local = (club_id, next_status) => {
    set_managed_clubs((prev) =>
      (prev || []).map((c) => {
        const cid = get_id(c);
        if (cid !== String(club_id)) return c;
        return {
          ...c,
          recruitmentStatus: next_status,
          recruitingStatus: next_status,
          recruitStatus: next_status,
          status: next_status,
        };
      }),
    );
  };

  const start_recruiting = async (club) => {
    const id = get_id(club);
    if (!id) return;

    if (is_recruiting_started(club)) {
      alert("이미 모집을 시작한 동아리입니다.");
      return;
    }

    const chk = await ensure_deadline_ok_for_start(id, club);
    if (!chk.ok) {
      if (chk.reason === "NO_DEADLINE") {
        alert("모집 마감일을 먼저 설정해야 모집을 시작할 수 있습니다.");
        return;
      }
      if (chk.reason === "DEADLINE_PASSED") {
        alert(
          "모집 마감일이 지난 동아리는 시작할 수 없습니다. 마감일을 수정해주세요.",
        );
        return;
      }
      alert("모집 시작 조건을 확인하지 못했습니다.");
      return;
    }

    const ok_confirm = window.confirm(
      "정말 모집을 시작하시겠습니까? 모집 시작을 한 이후에는 지원 양식을 편집할 수 없으며 모집 종료 이후에만 지원서 상세조회가 가능합니다.",
    );
    if (!ok_confirm) return;

    // ✅ 즉시 UI 반영: 글자 '모집중' + 시작 버튼 비활성 + 종료 버튼 활성
    // ✅ 즉시 '모집중'으로 보이게
    set_local_recruit_status((prev) => ({ ...prev, [id]: "OPEN" }));

    set_recruiting_loading_map((prev) => ({ ...prev, [id]: "start" }));

    try {
      const ok = await has_questions(id);
      if (!ok) {
        alert("지원서 양식을 먼저 등록해야 모집을 시작할 수 있습니다.");
        // 롤백
        set_local_recruit_status((prev) => {
          const next = { ...prev };
          delete next[id];
          return next;
        });
        await reload_owner_clubs();
        return;
      }

      await owner_start_recruitment(id);
      await reload_owner_clubs();
    } catch (e) {
      alert(e?.message || "모집 시작에 실패했습니다.");
      // 실패 롤백
      set_local_recruit_status((prev) => {
        const next = { ...prev };
        delete next[id];
        return next;
      });
      await reload_owner_clubs();
    } finally {
      set_recruiting_loading_map((prev) => ({ ...prev, [id]: null }));
    }
  };

  const close_recruiting = async (club) => {
    const id = get_id(club);
    if (!id) return;

    if (is_recruiting_closed(club)) {
      alert("이미 모집이 종료된 동아리입니다.");
      return;
    }

    const ok_confirm = window.confirm(
      "정말 모집을 종료하시겠습니까? 종료 후에도 마감일을 수정하면 다시 시작할 수 있습니다.",
    );
    if (!ok_confirm) return;

    // ✅ 즉시 UI 반영: 시작 불가 + 종료 완료 느낌
    // ✅ 즉시 '종료' 상태로 보이게
    set_local_recruit_status((prev) => ({ ...prev, [id]: "CLOSED" }));

    set_recruiting_loading_map((prev) => ({ ...prev, [id]: "close" }));

    try {
      await owner_close_recruitment(id);
      await reload_owner_clubs();
    } catch (e) {
      alert(e?.message || "모집 종료에 실패했습니다.");
      // 실패 롤백(일단 서버 데이터로 재동기화)
      set_local_recruit_status((prev) => {
        const next = { ...prev };
        delete next[id];
        return next;
      });
      await reload_owner_clubs();
    } finally {
      set_recruiting_loading_map((prev) => ({ ...prev, [id]: null }));
    }
  };

  useEffect(() => {
    if (!is_logged_in()) {
      navigate("/login");
      return;
    }

    const load = async () => {
      try {
        const nameData = await fetch_mypage_name();
        setName(nameData?.name || "");

        const appsData = await fetch_my_applications();
        setApplications(Array.isArray(appsData) ? appsData : []);

        try {
          await reload_owner_clubs();
        } catch (_) {
          set_managed_clubs([]);
          set_is_owner(false);
        }
      } catch (err) {
        set_error_msg(err?.message || "마이페이지 정보를 불러오지 못했습니다.");
      } finally {
        setLoading(false);
      }
    };

    load();
  }, [navigate]);

  const open_result_modal = async (club_id) => {
    set_result_open(true);
    set_result_loading(true);
    set_result_error("");
    set_result_data(null);

    try {
      const data = await fetch_application_result(club_id);
      set_result_data(data);
    } catch (e) {
      set_result_error(e?.message || "결과를 불러오지 못했습니다.");
    } finally {
      set_result_loading(false);
    }
  };

  const managed_ids = useMemo(() => {
    return new Set((managed_clubs || []).map(get_id).filter((v) => v != null));
  }, [managed_clubs]);

  const pure_applications = useMemo(() => {
    return (applications || [])
      .filter((a) => get_application_club_id(a) != null)
      .filter((a) => is_application_item(a))
      .filter((a) => !managed_ids.has(get_application_club_id(a)))
      .filter((a) => !hidden_ids.has(get_application_club_id(a)));
  }, [applications, managed_ids, hidden_ids]);

  const visible_managed_clubs = useMemo(() => {
    return (managed_clubs || [])
      .filter((c) => get_id(c) != null)
      .filter((c) => !hidden_ids.has(get_id(c)));
  }, [managed_clubs, hidden_ids]);

  const handleLogout = async () => {
    try {
      await apiLogout();
      navigate("/");
    } catch (err) {
      set_error_msg(err?.message || "로그아웃에 실패했습니다.");
    }
  };

  // const handleWithdraw = async () => {
  //   const ok = window.confirm("정말 탈퇴하시겠습니까? 되돌릴 수 없습니다.");
  //   if (!ok) return;

  //   try {
  //     await api_member_withdraw();
  //     alert("탈퇴가 완료되었습니다.");
  //     navigate("/");
  //   } catch (err) {
  //     set_error_msg(err?.message || "탈퇴에 실패했습니다.");
  //   }
  // };

  if (loading) {
    return (
      <div className="page-root">
        <div className="page-main mypage_main">
          <p>불러오는 중...</p>
        </div>
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
              onClick={() => navigate("/")}
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
            <h1>마이페이지</h1>
            <span className="header-name">{name}님</span>
          </div>
        </div>
      </div>

      <main className="page-main mypage_main">
        {error_msg && <p className="mypage_error">{error_msg}</p>}

        <section className="mypage_section">
          <h2 className="mypage_title">지원 목록</h2>

          {pure_applications.length === 0 ? (
            <div className="mypage_card">
              <p className="empty">아직 지원한 동아리가 없습니다.</p>
            </div>
          ) : (
            <div className="mypage_card">
              {pure_applications.map((app, idx) => {
                const club_id = get_application_club_id(app);
                const key = club_id ? `app-${club_id}` : `app-${idx}`;

                return (
                  <div className="club_box" key={key}>
                    <p className="club_title">{get_name(app)}</p>
                    <div className="club_buttons">
                      <button
                        onClick={() => {
                          if (!club_id)
                            return alert("동아리 id를 찾지 못했습니다.");
                          navigate(`/club/${club_id}`);
                        }}
                      >
                        동아리 페이지
                      </button>

                      <button
                        onClick={() => {
                          if (!club_id)
                            return alert("동아리 id를 찾지 못했습니다.");
                          navigate(`/apply_form_change/${club_id}`);
                        }}
                      >
                        지원서 편집
                      </button>

                      <button
                        onClick={() => {
                          if (!club_id)
                            return alert("동아리 id를 찾지 못했습니다.");
                          open_result_modal(club_id);
                        }}
                      >
                        결과 확인
                      </button>

                      {/* <button
                        onClick={() => {
                          if (!club_id)
                            return alert("동아리 id를 찾지 못했습니다.");
                          hide_club(club_id);
                        }}
                      >
                        삭제
                      </button> */}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </section>

        <Result
          open={result_open}
          loading={result_loading}
          error={result_error}
          result={result_data}
          onClose={() => set_result_open(false)}
        />

        {is_owner && (
          <section className="mypage_section">
            <h2 className="mypage_title">동아리 운영/관리</h2>

            <div className="mypage_card">
              {/* {hidden_ids.size > 0 && (
                <button className="add_btn" type="button" onClick={unhide_all}>
                  숨김 해제(전체)
                </button>
              )} */}

              {visible_managed_clubs.length === 0 ? (
                <p className="empty">운영 중인 동아리가 없습니다.</p>
              ) : (
                visible_managed_clubs.map((club) => {
                  const id = get_id(club);
                  const loading_kind = recruiting_loading_map[id];

                  const started = is_recruiting_started(club);
                  const deadline_passed = is_deadline_passed(club);
                  const closed = is_recruiting_closed(club);

                  const edit_form_disabled = started;

                  // 시작은: 이미 started면 불가, 마감일 지났으면 불가
                  const start_disabled = started || deadline_passed;

                  // 종료는: 모집중(started)일 때만 가능, 마감(혹은 마감일 지남)이면 불가
                  const stop_disabled = !started || closed || deadline_passed;

                  return (
                    <div className="club_box" key={`owner-${id}`}>
                      <p className="club_title">{get_name(club)}</p>

                      <div className="club_buttons">
                        <button onClick={() => navigate(`/club/${id}`)}>
                          동아리 페이지
                        </button>
                        <button onClick={() => navigate(`/club_manage/${id}`)}>
                          동아리 관리
                        </button>

                        <button
                          className={
                            edit_form_disabled ? "is_disabled_btn" : ""
                          }
                          onClick={() => {
                            if (edit_form_disabled) {
                              alert(
                                "모집을 시작한 이후에는 지원양식을 편집할 수 없습니다.",
                              );
                              return;
                            }
                            navigate(`/apply_form_edit/${id}`);
                          }}
                        >
                          지원양식 편집
                        </button>

                        <button
                          onClick={() => navigate(`/applicant_manage/${id}`)}
                        >
                          지원자 관리
                        </button>

                        <button
                          className="recruit_btn start"
                          disabled={
                            start_disabled ||
                            loading_kind === "start" ||
                            loading_kind === "close"
                          }
                          onClick={() => start_recruiting(club)}
                        >
                          {loading_kind === "start"
                            ? "처리중..."
                            : deadline_passed
                              ? "마감일 지남"
                              : started
                                ? "모집중"
                                : "모집시작"}
                        </button>

                        <button
                          className="recruit_btn stop"
                          disabled={
                            stop_disabled ||
                            loading_kind === "start" ||
                            loading_kind === "close"
                          }
                          onClick={() => close_recruiting(club)}
                        >
                          {loading_kind === "close"
                            ? "처리중..."
                            : closed
                              ? "종료완료"
                              : "모집종료"}
                        </button>

                        {/* <button onClick={() => hide_club(id)}>삭제</button> */}
                      </div>
                    </div>
                  );
                })
              )}

              <button
                className="add_btn"
                onClick={() => navigate("/club_edit")}
              >
                동아리 등록하기
              </button>
            </div>
          </section>
        )}

        <div className="mypage_footer">
          <button
            type="button"
            className="link_btn"
            onClick={() => navigate("/account_edit")}
          >
            회원정보수정
          </button>
          <button type="button" className="link_btn" onClick={handleLogout}>
            로그아웃
          </button>
          {/* <button
            type="button"
            className="link_btn logout_red"
            onClick={handleWithdraw}
          >
            탈퇴
          </button> */}
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
