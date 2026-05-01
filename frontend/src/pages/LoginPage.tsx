import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { toast } from 'sonner';
import { loginRequest } from '../api/authApi';
import { useAuthStore } from '../store/authStore';
import { ROUTES } from '../lib/routes';
import { ApiError } from '../lib/apiClient';

const loginSchema = z.object({
  email: z.string().min(1).email(),
  password: z.string().min(1),
});

type LoginFormValues = z.infer<typeof loginSchema>;

/**
 * Returns the redirectUrl ONLY if it's a same-origin internal path.
 * Rejects: protocol-relative `//evil.com`, absolute URLs `http://evil.com`,
 * empty strings, anything not starting with `/`.
 * Security: mitigates T-10-10 open-redirect attack via redirectUrl param.
 */
function safeRedirectUrl(raw: string | null): string {
  if (!raw) return ROUTES.HOME;
  if (!raw.startsWith('/')) return ROUTES.HOME;
  if (raw.startsWith('//')) return ROUTES.HOME;
  return raw;
}

export default function LoginPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const setSession = useAuthStore(s => s.setSession);
  const redirectUrl = safeRedirectUrl(params.get('redirectUrl'));

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    mode: 'onSubmit',
  });

  const mutation = useMutation({
    mutationFn: loginRequest,
    onSuccess: (res) => {
      setSession(res.accessToken, res.user);
      navigate(redirectUrl, { replace: true });
    },
    onError: (err) => {
      if (err instanceof ApiError && err.status === 401) {
        toast.error('E-posta veya şifre hatalı.');
        return;
      }
      const detail = err instanceof ApiError ? err.problem?.detail : undefined;
      toast.error(detail ?? 'Bir hata oluştu. Lütfen tekrar deneyiniz.');
    },
  });

  return (
    <div className="min-h-[calc(100vh-200px)] flex items-center justify-center px-4 py-8 bg-[var(--color-body-bg)]">
      <div className="bg-white rounded-lg p-8 w-full max-w-[420px] border border-[var(--color-border)]">
        <h1 className="text-xl font-bold mb-6 text-center">Giriş Yap</h1>
        <form
          onSubmit={handleSubmit(values => mutation.mutate(values))}
          noValidate
          className="space-y-4"
        >
          <div>
            <label htmlFor="email" className="block text-sm font-bold mb-1">
              E-posta
            </label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              {...register('email')}
              aria-invalid={errors.email ? 'true' : 'false'}
              className="w-full h-10 px-3 border border-[var(--color-border)] rounded focus:outline-2 focus:outline-[#1C1C1E]"
            />
            {errors.email && (
              <p role="alert" className="text-xs text-[#DC2626] mt-1">
                {errors.email.message}
              </p>
            )}
          </div>
          <div>
            <label htmlFor="password" className="block text-sm font-bold mb-1">
              Şifre
            </label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              {...register('password')}
              aria-invalid={errors.password ? 'true' : 'false'}
              className="w-full h-10 px-3 border border-[var(--color-border)] rounded focus:outline-2 focus:outline-[#1C1C1E]"
            />
            {errors.password && (
              <p role="alert" className="text-xs text-[#DC2626] mt-1">
                {errors.password.message}
              </p>
            )}
          </div>
          <button
            type="submit"
            disabled={mutation.isPending}
            className="w-full bg-[#1C1C1E] text-white py-3 rounded font-bold disabled:opacity-50"
          >
            {mutation.isPending ? "Giriş yapılıyor..." : "Giriş Yap"}
          </button>
        </form>
        <p className="text-center text-sm mt-6">
          Henüz üye değil misin?{' '}
          <Link
            to={ROUTES.REGISTER}
            className="font-bold text-[#1C1C1E] hover:underline"
          >
            Üye Ol
          </Link>
        </p>
      </div>
    </div>
  );
}
