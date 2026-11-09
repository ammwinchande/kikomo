import { type FormEvent, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import Logo from '@/logo';
import Button from '@/shared/ui/Button';
import { loginErrorText, useHealth, useLogin, useSession } from './hooks';

const pillars = [
  {tone: 'bg-allow-bg text-allow-fg', title: 'Allowed', text: 'Small, expected payments go straight through.'},
  {tone: 'bg-approve-bg text-approve-fg', title: 'Needs approval', text: 'Larger or unfamiliar payments wait for you.'},
  {tone: 'bg-deny-bg text-deny-fg', title: 'Denied', text: 'Anything that breaks the rules is stopped, with a reason.'},
];

export default function LoginPage() {
  const {status} = useSession();
  const health = useHealth();
  const login = useLogin();
  const navigate = useNavigate();
  const [password, setPassword] = useState('');

  if (status === 'authenticated') return <Navigate to="/approvals" replace/>;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!password) return;
    login.mutate(password, {onSuccess: () => navigate('/approvals', {replace: true})});
  };

  return (
    <div className="flex min-h-screen flex-col">
      <div className="h-1 bg-gradient-to-br from-brand to-teal" aria-hidden="true"/>
      <main className="mx-auto grid w-full max-w-shell flex-1 items-center gap-10 px-8 py-12 lg:grid-cols-[1.1fr_1fr]">
        <section className="hidden lg:block">
          <h1 className="text-4xl font-bold leading-tight tracking-tight">
            Let your AI agents pay.
            <br/>
            Keep the final say.
          </h1>
          <p className="mt-4 max-w-lg text-base text-muted">
            Every payment an agent attempts passes a fixed policy first. You approve the risky ones, stop the bad ones,
            and can always prove
            what happened.
          </p>
          <ul className="mt-8 grid gap-3">
            {pillars.map((p) => (
              <li key={p.title}
                  className="flex items-start gap-3 rounded-xl border border-line bg-white p-4 shadow-card">
                <span className={`mt-0.5 rounded-full px-2.5 py-1 text-xs font-semibold ${p.tone}`}>{p.title}</span>
                <span className="text-sm text-muted">{p.text}</span>
              </li>
            ))}
          </ul>
        </section>

        <section className="mx-auto w-full max-w-md rounded-2xl border border-line bg-white p-8 shadow-card">
          <div className="flex items-center gap-3">
            <Logo size={40}/>
            <div>
              <div className="text-xl font-bold tracking-tight">Kikomo</div>
              <div className="text-xs text-muted">Sign in to the admin portal</div>
            </div>
          </div>
          <p className="mt-5 text-sm text-muted">Approve payments, review activity and control your agents.</p>
          <form onSubmit={submit} className="mt-6 grid gap-4" aria-label="Sign in">
            <label className="grid gap-1.5 text-sm font-medium" htmlFor="password">
              Admin password
              <input
                id="password"
                type="password"
                name="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="rounded-lg border border-line bg-white px-3 py-2.5 text-sm text-ink"
                placeholder="Enter the admin password"
              />
            </label>
            {login.isError && (
              <p role="alert" className="rounded-lg bg-deny-bg px-3 py-2 text-sm text-deny-fg">
                {loginErrorText(login.error)}
              </p>
            )}
            <Button type="submit" loading={login.isPending} disabled={!password}>
              Sign in
            </Button>
          </form>
          <p className="mt-5 text-xs text-muted">Demo access: the password is shown in the README.</p>
          <p
            className="mt-2 inline-flex items-center gap-1.5 rounded-full bg-chip px-2.5 py-1 text-xs font-semibold text-brand-deep">
            {health.data?.paypal_mode === 'live' ? 'PayPal sandbox only. No real money moves.' : 'Mock PayPal. Nothing is sent anywhere.'}
          </p>
        </section>
      </main>
    </div>
  );
}
