import { Button, Field, Input } from "@ogcloud/ui";
import { Dices } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";

// Letters and digits without look-alikes (0/O, 1/l/I), so a generated password can be read out or typed.
const ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789";
const GENERATED_LENGTH = 20;
// The API takes 12 to 72 bytes (bcrypt's limit, D-0094).
const MIN_BYTES = 12;
const MAX_BYTES = 72;

/** A random password from the browser's CSPRNG, without modulo bias. */
export function generatePassword(): string {
  const limit = 256 - (256 % ALPHABET.length);
  let password = "";
  while (password.length < GENERATED_LENGTH) {
    for (const byte of crypto.getRandomValues(
      new Uint8Array(GENERATED_LENGTH),
    )) {
      if (byte < limit && password.length < GENERATED_LENGTH) {
        password += ALPHABET[byte % ALPHABET.length];
      }
    }
  }
  return password;
}

export function isValidPassword(password: string): boolean {
  const bytes = new TextEncoder().encode(password).length;
  return bytes >= MIN_BYTES && bytes <= MAX_BYTES;
}

/** A password an admin sets for someone else: typed, or generated and shown so it can be passed on. */
export function NewPasswordField({
  value,
  onChange,
}: {
  value: string;
  onChange: (password: string) => void;
}) {
  const { t } = useTranslation("web");
  const [shown, setShown] = useState(false);
  return (
    <Field label={t("users.password.label")} hint={t("users.password.hint")}>
      {(control) => (
        <div className="flex gap-2">
          <Input
            {...control}
            className="flex-1"
            mono={shown}
            type={shown ? "text" : "password"}
            autoComplete="new-password"
            value={value}
            onChange={(event) => onChange(event.target.value)}
          />
          <Button
            icon={Dices}
            onClick={() => {
              onChange(generatePassword());
              setShown(true);
            }}
          >
            {t("users.password.generate")}
          </Button>
        </div>
      )}
    </Field>
  );
}
