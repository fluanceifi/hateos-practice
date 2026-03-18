// src/main.jsx
import React from "react";
import ReactDOM from "react-dom/client";
import { RouterProvider } from "react-router-dom";
import { router } from "./app/router.jsx";
import { on_session_expired, clear_tokens } from "./lib/api";

let unsub = null;

function setup_session_expired_listener() {
  if (unsub) return;

  unsub = on_session_expired(() => {
    clear_tokens();

    const next = `${window.location.pathname}${window.location.search || ""}`;
    const q = encodeURIComponent(next && next !== "/login" ? next : "/");

    try {
      router.navigate(`/login?next=${q}`);
    } catch {
      window.location.href = `/login?next=${q}`;
    }
  });
}

setup_session_expired_listener();

ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <RouterProvider router={router} />
  </React.StrictMode>,
);
