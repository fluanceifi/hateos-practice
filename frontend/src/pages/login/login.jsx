// src/pages/login/login.jsx
import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import "../../styles/globals.css";
import "./login.css";
import { apiLogin } from "../../lib/api";

function safe_next_path(raw) {
  if (!raw) return "/";
  const s = String(raw);

  // 내부 경로만 허용 (오픈리다이렉트 방지)
  if (!s.startsWith("/")) return "/";
  if (s.startsWith("//")) return "/";

  // 로그인/회원가입으로 루프 방지
  if (s.startsWith("/login") || s.startsWith("/signup")) return "/";

  return s;
}

export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();

  const next_path = useMemo(() => {
    const sp = new URLSearchParams(location.search || "");
    return safe_next_path(sp.get("next"));
  }, [location.search]);

  // form state
  const [student_id, set_student_id] = useState("");
  const [password, set_password] = useState("");
  const [show_password, set_show_password] = useState(false);
  const [is_loading, set_is_loading] = useState(false);
  const [remember_me, set_remember_me] = useState(false);
  const [error_msg, set_error_msg] = useState("");

  // remember me (학번 저장)
  useEffect(() => {
    const saved_id = localStorage.getItem("smu_student_id");
    if (saved_id) {
      set_student_id(saved_id);
      set_remember_me(true);
    }
  }, []);

  const on_toggle_password = () => set_show_password((v) => !v);

  const on_submit = async (e) => {
    e.preventDefault();
    set_error_msg("");
    set_is_loading(true);

    try {
      await apiLogin({
        studentId: student_id,
        password,
      });

      if (remember_me) {
        localStorage.setItem("smu_student_id", student_id);
      } else {
        localStorage.removeItem("smu_student_id");
      }

      navigate(next_path || "/");
    } catch (err) {
      if (err.code === "UNAUTHORIZED") {
        set_error_msg("인증이 필요합니다.");
      } else if (err.code === "EXPIRED_TOKEN") {
        set_error_msg("세션이 만료되었습니다. 다시 로그인해주세요.");
      } else {
        set_error_msg(err.message || "로그인에 실패했습니다.");
      }
    } finally {
      set_is_loading(false);
    }
  };

  return (
    <div className="page-root">
      {/* Header */}
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
            <h1>로그인</h1>
          </div>
        </div>
      </div>

      {/* Main */}
      <main className="page-main">
        <div className="auth_page">
          {/* Hero Section */}
          <div className="hero-section">
            <div className="hero-content">
              <img
                src="/images/2.png"
                alt="스뮤 클럽 로고"
                className="hero-logo"
              />
              <p style={{ marginTop: "8px" }}>상명대학교 동아리 통합 플랫폼</p>
            </div>
          </div>

          {/* Login Form */}
          <div className="form-container">
            <div className="card">
              <div className="card-header">
                <h3>학번과 비밀번호를 입력해 주세요</h3>
                <p
                  aria-live="polite"
                  style={{ minHeight: 20, color: "#a82d2f" }}
                >
                  {error_msg || ""}
                </p>
              </div>

              <form
                className="login-form"
                autoComplete="off"
                onSubmit={on_submit}
              >
                {/* 학번 입력 */}
                <div className="form-group">
                  <label htmlFor="studentId">학번</label>
                  <div className="input-container">
                    <svg
                      className="input-icon"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                    >
                      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                      <circle cx="12" cy="7" r="4" />
                    </svg>
                    <input
                      type="text"
                      id="studentId"
                      placeholder="학번을 입력해주세요"
                      required
                      inputMode="numeric"
                      value={student_id}
                      onChange={(e) => set_student_id(e.target.value)}
                    />
                  </div>
                </div>

                {/* 비밀번호 입력 */}
                <div className="form-group">
                  <label htmlFor="password">비밀번호</label>
                  <div className="input-container">
                    <svg
                      className="input-icon"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                    >
                      <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                      <circle cx="12" cy="16" r="1" />
                      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                    </svg>
                    <input
                      type={show_password ? "text" : "password"}
                      id="password"
                      placeholder="비밀번호를 입력하세요"
                      required
                      value={password}
                      onChange={(e) => set_password(e.target.value)}
                    />
                    <button
                      type="button"
                      className="password-toggle"
                      aria-label="비밀번호 보기 전환"
                      onClick={on_toggle_password}
                    >
                      <svg
                        className="icon"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                      >
                        <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                        <circle cx="12" cy="12" r="3" />
                      </svg>
                    </button>
                  </div>
                </div>

                {/* 옵션 */}
                <div className="form-options">
                  <label className="checkbox-label">
                    <input
                      type="checkbox"
                      checked={remember_me}
                      onChange={(e) => set_remember_me(e.target.checked)}
                    />
                    <span className="checkmark"></span>
                    로그인 상태 유지
                  </label>
                  <a
                    href="https://smsso.smu.ac.kr/svc/tk/Auth.do?ac=Y&ifa=N&id=portal&"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="forgot-password"
                  >
                    비밀번호를 잊어버리셨나요?
                  </a>
                </div>

                {/* 로그인 버튼 */}
                <button
                  type="submit"
                  className="btn btn-primary btn-large"
                  id="loginBtn"
                  disabled={is_loading}
                >
                  <span id="loginText">
                    {is_loading ? "로그인 중..." : "로그인"}
                  </span>
                  <div
                    className="spinner"
                    aria-hidden="true"
                    style={{
                      display: is_loading ? "inline-block" : "none",
                    }}
                  />
                </button>

                {/* 회원가입 버튼 */}
                <Link to="/signup" className="btn btn-secondary btn-large">
                  회원가입 (학생 인증)
                </Link>
              </form>

              <div className="form-footer">
                <p>회원가입 시 상명대학교 샘물포털시스템을 통해</p>
                <p>재학생 인증을 진행합니다</p>
              </div>
            </div>
          </div>
        </div>
      </main>

      {/* Footer */}
      <div className="page-footer">
        <p>© 2025 smu-club. 상명대학교 동아리 통합 플랫폼</p>
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
