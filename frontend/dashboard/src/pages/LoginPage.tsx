import { startTransition, useState, type SubmitEventHandler } from "react";
import { motion } from "motion/react";
import { useLocation, useNavigate } from "react-router";
import { FiArrowRight, FiEye, FiEyeOff, FiLock, FiMail } from "react-icons/fi";

import AppToasts from "@/components/AppToasts";
import { useAuthStore } from "@/store/auth-store";

const LoginPage = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const login = useAuthStore((state) => state.login);
  const status = useAuthStore((state) => state.status);

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault();
    setErrorMessage(null);

    try {
      await login({
        email: email.trim(),
        password,
      });

      const nextState = location.state as { from?: string } | null;
      const nextPath = typeof nextState?.from === "string" ? nextState.from : "/";

      startTransition(() => {
        navigate(nextPath, { replace: true });
      });
    } catch (error) {
      const nextMessage =
        error instanceof Error
          ? error.message
          : "Unable to sign in. Please try again.";

      setErrorMessage(nextMessage);
    }
  };

  const isSubmitting = status === "authenticating";

  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center bg-background-dark p-4 text-slate-100">
      <AppToasts
        items={
          errorMessage
            ? [
                {
                  id: "login-error",
                  message: errorMessage,
                  onDismiss: () => setErrorMessage(null),
                  tone: "error" as const,
                },
              ]
            : []
        }
      />

      <div
        className="pointer-events-none fixed inset-0 -z-10 overflow-hidden"
        aria-hidden="true"
      >
        <div className="absolute -left-[10%] -top-[10%] h-[40%] w-[40%] rounded-full bg-primary/5 blur-[120px]" />
        <div className="absolute -bottom-[10%] -right-[10%] h-[40%] w-[40%] rounded-full bg-primary/10 blur-[120px]" />
      </div>

      <motion.div
        initial={{ y: 20, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.35, ease: "easeOut" }}
        className="flex w-full max-w-120 flex-col items-center"
      >
        <div className="mb-8 flex flex-col items-center">
          <div className="mb-6 flex items-center gap-3">
            <img
              src="/static/logo.webp"
              alt="OgCloud"
              className="h-10 w-auto"
            />
            <h2 className="text-2xl font-bold tracking-tight text-white">OgCloud</h2>
          </div>
          <h1 className="mb-2 text-center text-3xl font-bold leading-tight text-white">
            Welcome Back
          </h1>
          <p className="text-center text-sm font-normal text-slate-400">
            Enter your credentials to access the dashboard
          </p>
        </div>

        <div className="w-full rounded-xl border border-slate-800 bg-slate-900/50 p-8 shadow-xl backdrop-blur-sm">
          <form onSubmit={handleSubmit} className="space-y-6">
            <div className="app-field-stack">
              <label className="app-field-label">
                Email Address
              </label>
              <div className="group relative">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400 transition-colors group-focus-within:text-primary">
                  <FiMail className="h-5 w-5" />
                </div>
                <input
                  type="email"
                  value={email}
                  onChange={(event) => {
                    setEmail(event.target.value);
                    if (errorMessage) {
                      setErrorMessage(null);
                    }
                  }}
                  placeholder="name@company.com"
                  autoComplete="email"
                  required
                  className="app-input-field block w-full rounded-lg border border-slate-700 bg-slate-800 py-3 pl-10 pr-4 text-slate-100 transition-all placeholder:text-slate-500 focus:border-primary focus:ring-2 focus:ring-primary/20 focus:outline-none"
                />
              </div>
            </div>

            <div className="app-field-stack">
              <div className="flex items-center justify-between">
                <label className="app-field-label">
                  Password
                </label>
              </div>
              <div className="group relative">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400 transition-colors group-focus-within:text-primary">
                  <FiLock className="h-5 w-5" />
                </div>
                <input
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(event) => {
                    setPassword(event.target.value);
                    if (errorMessage) {
                      setErrorMessage(null);
                    }
                  }}
                  placeholder="Password"
                  autoComplete="current-password"
                  required
                  className="app-input-field block w-full rounded-lg border border-slate-700 bg-slate-800 py-3 pl-10 pr-12 text-slate-100 transition-all placeholder:text-slate-500 focus:border-primary focus:ring-2 focus:ring-primary/20 focus:outline-none"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((currentValue) => !currentValue)}
                  className="button-hover-lift absolute inset-y-0 right-0 flex items-center rounded-r-lg pr-3 text-slate-400 transition-colors hover:text-slate-200"
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? (
                    <FiEyeOff className="h-5 w-5" />
                  ) : (
                    <FiEye className="h-5 w-5" />
                  )}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className="app-button-field button-hover-lift button-shadow-primary flex w-full items-center justify-center gap-2 rounded-lg bg-primary px-4 py-3.5 font-bold text-slate-950 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-70"
            >
              <span>{isSubmitting ? "Signing In" : "Sign In"}</span>
              <FiArrowRight className="h-4 w-4" />
            </button>
          </form>

          <div className="mt-8 border-t border-slate-800 pt-6">
            <p className="text-center text-xs uppercase tracking-widest text-slate-500">
              OAuth Providers coming soon!
            </p>
          </div>
        </div>

        <div className="mt-10 text-center">
          <div className="mt-8 flex items-center justify-center gap-6 text-xs font-semibold uppercase tracking-widest text-slate-500">
            <span className="transition-colors hover:text-primary">Website</span>
            <span className="size-1 rounded-full bg-slate-700" />
            <span className="transition-colors hover:text-primary">Discord</span>
            <span className="size-1 rounded-full bg-slate-700" />
            <span className="transition-colors hover:text-primary">Support</span>
          </div>
        </div>
      </motion.div>
    </div>
  );
};

export default LoginPage;
