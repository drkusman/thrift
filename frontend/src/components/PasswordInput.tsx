"use client";

import { useState } from "react";
import { IconEye, IconEyeOff } from "./icons";

type Props = {
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  minLength?: number;
  autoFocus?: boolean;
  id?: string;
  autoComplete?: "current-password" | "new-password";
  disabled?: boolean;
};

export function PasswordInput({ value, onChange, required, minLength, autoFocus, id, autoComplete = "current-password", disabled }: Props) {
  const [visible, setVisible] = useState(false);

  return (
    <div className="pw-field">
      <input
        id={id}
        type={visible ? "text" : "password"}
        className="field-input"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        required={required}
        minLength={minLength}
        autoFocus={autoFocus}
        autoComplete={autoComplete}
        disabled={disabled}
      />
      <button
        type="button"
        onClick={() => setVisible((v) => !v)}
        className="pw-toggle"
        tabIndex={-1}
        aria-label={visible ? "Hide password" : "Show password"}
        title={visible ? "Hide password" : "Show password"}
      >
        {visible ? <IconEyeOff className="w-4 h-4" /> : <IconEye className="w-4 h-4" />}
      </button>
    </div>
  );
}
