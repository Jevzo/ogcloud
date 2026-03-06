import { useCallback, useEffect, useState, type SubmitEventHandler } from "react";
import { motion } from "motion/react";
import { FiKey, FiLink, FiMail, FiSave, FiUser } from "react-icons/fi";

import AppToasts from "@/components/AppToasts";
import { loginWithEmailPassword, updateOwnProfile } from "@/lib/api";
import { useAuthStore } from "@/store/auth-store";

const SettingsPage = () => {
  const session = useAuthStore((state) => state.session);
  const refreshIfNeeded = useAuthStore((state) => state.refreshIfNeeded);
  const updateUser = useAuthStore((state) => state.updateUser);
  const setSession = useAuthStore((state) => state.setSession);

  const currentUser = session?.user ?? null;
  const linkedPlayerHeadUrl = currentUser?.linkedPlayerUuid
    ? `https://mc-heads.net/avatar/${currentUser.linkedPlayerUuid}`
    : null;

  const [email, setEmail] = useState(currentUser?.email ?? "");
  const [emailMessage, setEmailMessage] = useState<string | null>(null);
  const [emailError, setEmailError] = useState<string | null>(null);
  const [isSavingEmail, setIsSavingEmail] = useState(false);

  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordMessage, setPasswordMessage] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [isSavingPassword, setIsSavingPassword] = useState(false);

  useEffect(() => {
    setEmail(currentUser?.email ?? "");
  }, [currentUser?.email]);

  const getValidAccessToken = useCallback(async () => {
    const nextSession = await refreshIfNeeded();

    if (!nextSession) {
      throw new Error("Your session expired. Please sign in again.");
    }

    return nextSession.accessToken;
  }, [refreshIfNeeded]);

  const handleEmailSave: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault();
    setEmailMessage(null);
    setEmailError(null);

    if (!email.trim()) {
      setEmailError("Email is required.");
      return;
    }

    if (email.trim() === currentUser?.email) {
      setEmailMessage("Your email is already up to date.");
      return;
    }

    setIsSavingEmail(true);

    try {
      const accessToken = await getValidAccessToken();
      const updatedUser = await updateOwnProfile(accessToken, { email: email.trim() });
      updateUser(updatedUser);
      setEmail(updatedUser.email);
      setEmailMessage("Email updated.");
    } catch (error) {
      setEmailError(error instanceof Error ? error.message : "Unable to update email.");
    } finally {
      setIsSavingEmail(false);
    }
  };

  const handlePasswordSave: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault();
    setPasswordMessage(null);
    setPasswordError(null);

    if (!password) {
      setPasswordError("Password is required.");
      return;
    }

    if (password !== confirmPassword) {
      setPasswordError("Passwords do not match.");
      return;
    }

    if (!currentUser?.email) {
      setPasswordError("No authenticated user available.");
      return;
    }

    setIsSavingPassword(true);

    try {
      const accessToken = await getValidAccessToken();
      await updateOwnProfile(accessToken, { password });

      const nextSession = await loginWithEmailPassword({
        email: currentUser.email,
        password,
      });

      setSession(nextSession);
      setPassword("");
      setConfirmPassword("");
      setPasswordMessage("Password updated and session refreshed.");
    } catch (error) {
      setPasswordError(error instanceof Error ? error.message : "Unable to update password.");
    } finally {
      setIsSavingPassword(false);
    }
  };

  return (
    <div className="flex h-full flex-col gap-6">
      <AppToasts
        items={[
          ...(emailError
            ? [
                {
                  id: "settings-email-error",
                  message: emailError,
                  onDismiss: () => setEmailError(null),
                  tone: "error" as const,
                },
              ]
            : []),
          ...(emailMessage
            ? [
                {
                  id: "settings-email-success",
                  message: emailMessage,
                  onDismiss: () => setEmailMessage(null),
                  tone: "success" as const,
                },
              ]
            : []),
          ...(passwordError
            ? [
                {
                  id: "settings-password-error",
                  message: passwordError,
                  onDismiss: () => setPasswordError(null),
                  tone: "error" as const,
                },
              ]
            : []),
          ...(passwordMessage
            ? [
                {
                  id: "settings-password-success",
                  message: passwordMessage,
                  onDismiss: () => setPasswordMessage(null),
                  tone: "success" as const,
                },
              ]
            : []),
        ]}
      />

      <motion.section
        initial={{ y: 12, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.35, ease: "easeOut" }}
        className="flex items-center justify-between"
      >
        <h2 className="flex items-center gap-2 text-lg font-bold text-white">
          <FiUser className="h-5 w-5 text-primary" />
          Settings
        </h2>
        <span className="text-xs text-slate-500">Manage your own account</span>
      </motion.section>

      <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
        <motion.section
          initial={{ y: 16, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          transition={{ duration: 0.35, ease: "easeOut", delay: 0.05 }}
          className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm"
        >
          <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
            <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
              Profile Details
            </h3>
          </div>
          <div className="space-y-8 p-6">
            <form onSubmit={handleEmailSave} className="space-y-4">
              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                  <FiMail className="h-5 w-5" />
                </div>
                <div>
                  <h4 className="text-base font-semibold text-white">Email</h4>
                  <p className="text-sm text-slate-400">
                    Update the email used for dashboard sign-in.
                  </p>
                </div>
              </div>

              <label className="app-field-stack">
                <span className="app-field-label">Email Address</span>
                <input
                  type="email"
                  value={email}
                  onChange={(event) => {
                    setEmail(event.target.value);
                    if (emailError) {
                      setEmailError(null);
                    }
                    if (emailMessage) {
                      setEmailMessage(null);
                    }
                  }}
                  className="app-input-field block w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-3 text-slate-100 transition-all placeholder:text-slate-500 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                  required
                />
              </label>

              <button
                type="submit"
                disabled={isSavingEmail}
                className="app-button-field button-hover-lift button-shadow-primary inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-slate-950 transition-colors disabled:cursor-not-allowed disabled:opacity-70"
              >
                <FiSave className="h-4 w-4" />
                <span>{isSavingEmail ? "Saving..." : "Save Email"}</span>
              </button>
            </form>

            <form onSubmit={handlePasswordSave} className="space-y-4 border-t border-slate-800 pt-8">
              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                  <FiKey className="h-5 w-5" />
                </div>
                <div>
                  <h4 className="text-base font-semibold text-white">Password</h4>
                  <p className="text-sm text-slate-400">
                    Change your password and immediately refresh your session.
                  </p>
                </div>
              </div>

              <label className="app-field-stack">
                <span className="app-field-label">New Password</span>
                <input
                  type="password"
                  value={password}
                  onChange={(event) => {
                    setPassword(event.target.value);
                    if (passwordError) {
                      setPasswordError(null);
                    }
                    if (passwordMessage) {
                      setPasswordMessage(null);
                    }
                  }}
                  className="app-input-field block w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-3 text-slate-100 transition-all placeholder:text-slate-500 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                  required
                />
              </label>

              <label className="app-field-stack">
                <span className="app-field-label">Confirm New Password</span>
                <input
                  type="password"
                  value={confirmPassword}
                  onChange={(event) => {
                    setConfirmPassword(event.target.value);
                    if (passwordError) {
                      setPasswordError(null);
                    }
                    if (passwordMessage) {
                      setPasswordMessage(null);
                    }
                  }}
                  className="app-input-field block w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-3 text-slate-100 transition-all placeholder:text-slate-500 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                  required
                />
              </label>

              <button
                type="submit"
                disabled={isSavingPassword}
                className="app-button-field button-hover-lift button-shadow-primary inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-slate-950 transition-colors disabled:cursor-not-allowed disabled:opacity-70"
              >
                <FiSave className="h-4 w-4" />
                <span>{isSavingPassword ? "Saving..." : "Save Password"}</span>
              </button>
            </form>
          </div>
        </motion.section>

        <motion.section
          initial={{ y: 16, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          transition={{ duration: 0.35, ease: "easeOut", delay: 0.09 }}
          className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm"
        >
          <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
            <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
              Minecraft Account
            </h3>
          </div>
          <div className="space-y-6 p-6">
            <div className="flex items-center gap-4">
              {linkedPlayerHeadUrl ? (
                <img
                  src={linkedPlayerHeadUrl}
                  alt={`${currentUser?.username ?? "User"} Minecraft avatar`}
                  className="h-16 w-16 rounded-xl bg-slate-800 object-cover"
                  referrerPolicy="no-referrer"
                />
              ) : (
                <div className="flex h-16 w-16 items-center justify-center rounded-xl bg-primary/10 text-primary">
                  <FiLink className="h-7 w-7" />
                </div>
              )}

              <div>
                <h4 className="text-base font-semibold text-white">
                  {currentUser?.linkedPlayerUuid ? "Linked Account" : "No Linked Account"}
                </h4>
                <p className="mt-1 text-sm text-slate-400">
                  {currentUser?.linkedPlayerUuid
                    ? `Connected to ${currentUser.username}`
                    : "Your account must be linked before using the dashboard."}
                </p>
              </div>
            </div>

            {currentUser?.linkedPlayerUuid && (
              <div className="rounded-lg border border-slate-800 bg-slate-800/40 px-4 py-3 text-sm text-slate-300">
                <div className="font-medium text-white">Player UUID</div>
                <div className="mt-1 break-all font-mono text-xs text-slate-400">
                  {currentUser.linkedPlayerUuid}
                </div>
              </div>
            )}
          </div>
        </motion.section>
      </div>
    </div>
  );
};

export default SettingsPage;
