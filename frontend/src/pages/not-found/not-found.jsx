import { Link } from "react-router-dom";

export default function NotFound() {
  return (
    <div
      style={{
        maxWidth: 390,
        margin: "0 auto",
        padding: 16,
        textAlign: "center",
      }}
    >
      <h2>페이지를 찾을 수 없습니다.</h2>
      <p style={{ color: "#6b7280", marginTop: 8 }}>
        잘못된 경로이거나 삭제된 페이지입니다.
      </p>
      <p style={{ marginTop: 16 }}>
        <Link to="/">홈으로</Link>
      </p>
    </div>
  );
}
