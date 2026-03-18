// src/pages/account_edit/account_edit.jsx
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  fetch_mypage_profile,
  fetch_mypage_name,
  update_mypage_email,
  update_mypage_phone,
} from "../../lib/api";
import "../../styles/globals.css";
import "./account_edit.css";

export default function AccountEdit() {
  const navigate = useNavigate();

  const [profile, setProfile] = useState({
    name: "",
    email: "",
    phoneNumber: "",
  });

  const [newEmail, setNewEmail] = useState("");
  const [newPhone, setNewPhone] = useState("");

  useEffect(() => {
    async function load_profile() {
      try {
        const [profile_data, name_data] = await Promise.all([
          fetch_mypage_profile(), // { memberId, email, phoneNumber }
          fetch_mypage_name(), // { name }
        ]);

        setProfile({
          name: name_data?.name ?? "",
          email: profile_data?.email ?? "",
          phoneNumber: profile_data?.phoneNumber ?? "",
        });
      } catch (e) {
        console.error("failed to fetch mypage data", e);
      }
    }
    load_profile();
  }, []);

  const handle_email_save = async () => {
    const trimmed = newEmail.trim();
    if (!trimmed) return;

    try {
      await update_mypage_email(trimmed);

      const latest = await fetch_mypage_profile();
      setProfile((prev) => ({
        ...prev,
        email: latest?.email ?? prev.email,
      }));

      setNewEmail("");
      alert("이메일이 수정되었습니다.");
    } catch (e) {
      console.error("failed to update email", e);
      alert("이메일 수정에 실패했습니다.");
    }
  };

  const handle_phone_save = async () => {
    const trimmed = newPhone.trim();
    if (!trimmed) return;

    try {
      await update_mypage_phone(trimmed);

      const latest = await fetch_mypage_profile();
      setProfile((prev) => ({
        ...prev,
        phoneNumber: latest?.phoneNumber ?? prev.phoneNumber,
      }));

      setNewPhone("");
      alert("전화번호가 수정되었습니다.");
    } catch (e) {
      console.error("failed to update phone", e);
      alert("전화번호 수정에 실패했습니다.");
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
            <h1>회원정보수정</h1>
            <span className="header-name">
              {profile.name ? `${profile.name}님` : "회원님"}
            </span>
          </div>
        </div>
      </div>

      {/* Main */}
      <main className="page-main account_main">
        {/* 이메일 수정 */}
        <section className="account_section">
          <h2 className="account_title">이메일 수정</h2>
          <div className="account_card">
            <label className="field_label" htmlFor="currentEmail">
              기존 이메일
            </label>
            <input
              className="field_input"
              id="currentEmail"
              type="email"
              value={profile.email}
              readOnly
            />

            <label className="field_label" htmlFor="newEmail">
              새 이메일
            </label>
            <input
              className="field_input"
              id="newEmail"
              type="email"
              placeholder="새 이메일"
              value={newEmail}
              onChange={(e) => setNewEmail(e.target.value)}
            />

            <button
              className="primary_btn"
              type="button"
              onClick={handle_email_save}
            >
              저장하기
            </button>
          </div>
        </section>

        {/* 전화번호 수정 */}
        <section className="account_section">
          <h2 className="account_title">전화번호 수정</h2>
          <div className="account_card">
            <label className="field_label" htmlFor="currentPhone">
              기존 전화번호
            </label>
            <input
              className="field_input"
              id="currentPhone"
              type="tel"
              value={profile.phoneNumber}
              readOnly
            />

            <label className="field_label" htmlFor="newPhone">
              새 전화번호 (- 없이 숫자만 입력)
            </label>
            <input
              className="field_input"
              id="newPhone"
              type="tel"
              placeholder="새 전화번호"
              value={newPhone}
              onChange={(e) => setNewPhone(e.target.value)}
            />

            <button
              className="primary_btn"
              type="button"
              onClick={handle_phone_save}
            >
              저장하기
            </button>
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
