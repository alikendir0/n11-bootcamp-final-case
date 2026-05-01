import { useForm, type UseFormRegisterReturn } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { toast } from 'sonner';
import { registerRequest } from '../api/authApi';
import { useAuthStore } from '../store/authStore';
import { ROUTES } from '../lib/routes';
import { ApiError } from '../lib/apiClient';

const PASSWORD_POLICY_MESSAGE = 'Şifre en az 8 karakter olmalı, en az bir harf ve bir rakam içermelidir.';

const registerSchema = z
  .object({
    fullName: z.string().min(2, { message: 'Ad Soyad en az 2 karakter olmalıdır.' }),
    email: z.string().min(1).email(),
    password: z.string().regex(/^(?=.*[A-Za-zÇĞİÖŞÜçğıöşü])(?=.*\d).{8,}$/, {
      message: PASSWORD_POLICY_MESSAGE,
    }),
    passwordConfirm: z.string().min(1),
  })
  .refine(d => d.password === d.passwordConfirm, {
    path: ['passwordConfirm'],
    message: 'Şifreler eşleşmiyor.',
  });

type RegisterFormValues = z.infer<typeof registerSchema>;

export default function RegisterPage() {
  const navigate = useNavigate();
  const setSession = useAuthStore(s => s.setSession);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    mode: 'onSubmit',
  });

  const mutation = useMutation({
    mutationFn: (values: RegisterFormValues) =>
      registerRequest({
        email: values.email,
        password: values.password,
        fullName: values.fullName,
      }),
    onSuccess: (res) => {
      setSession(res.accessToken, res.user);
      toast.success('Hesabınız oluşturuldu, hoş geldiniz.');
      navigate(ROUTES.HOME, { replace: true });
    },
    onError: (err) => {
      if (err instanceof ApiError && err.status === 409) {
        toast.error(err.problem?.detail ?? 'Bu e-posta adresi zaten kullanımda.');
        return;
      }
      if (err instanceof ApiError && err.status === 400) {
        setError('root.server', {
          message: err.problem?.detail ?? 'Kayıt bilgilerinizi kontrol ediniz.',
        });
        return;
      }
      const detail = err instanceof ApiError ? err.problem?.detail : undefined;
      toast.error(detail ?? 'Bir hata oluştu. Lütfen tekrar deneyiniz.');
    },
  });

  return (
    <div className="min-h-[calc(100vh-200px)] flex items-center justify-center px-4 py-8 bg-[var(--color-body-bg)]">
      <div className="bg-white rounded-lg p-8 w-full max-w-[420px] border border-[var(--color-border)]">
        <h1 className="text-xl font-bold mb-6 text-center">Üye Ol</h1>
        <form
          onSubmit={handleSubmit(values => mutation.mutate(values))}
          noValidate
          className="space-y-4"
        >
          <FormField
            id="fullName"
            label="Ad Soyad"
            type="text"
            autoComplete="name"
            error={errors.fullName?.message}
            registration={register('fullName')}
          />
          <FormField
            id="email"
            label="E-posta"
            type="email"
            autoComplete="email"
            error={errors.email?.message}
            registration={register('email')}
          />
          <FormField
            id="password"
            label="Şifre"
            type="password"
            autoComplete="new-password"
            error={errors.password?.message}
            registration={register('password')}
          />
          <FormField
            id="passwordConfirm"
            label="Şifre (tekrar)"
            type="password"
            autoComplete="new-password"
            error={errors.passwordConfirm?.message}
            registration={register('passwordConfirm')}
          />
          <button
            type="submit"
            disabled={mutation.isPending}
            aria-label="Üye Ol"
            className="w-full bg-[#1C1C1E] text-white py-3 rounded font-bold disabled:opacity-50"
          >
            {mutation.isPending ? "Kayıt yapılıyor..." : "Üye Ol"}
          </button>
          {errors.root?.server?.message && (
            <p role="alert" className="text-sm text-[#DC2626] bg-red-50 border border-red-100 rounded p-3">
              {errors.root.server.message}
            </p>
          )}
        </form>
        <p className="text-center text-sm mt-6">
          Zaten üye misin?{' '}
          <Link
            to={ROUTES.LOGIN}
            className="font-bold text-[#1C1C1E] hover:underline"
          >
            Giriş Yap
          </Link>
        </p>
      </div>
    </div>
  );
}

interface FormFieldProps {
  id: string;
  label: string;
  type: 'text' | 'email' | 'password';
  autoComplete: string;
  error: string | undefined;
  registration: UseFormRegisterReturn;
}

function FormField({ id, label, type, autoComplete, error, registration }: FormFieldProps) {
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-bold mb-1">
        {label}
      </label>
      <input
        id={id}
        type={type}
        autoComplete={autoComplete}
        {...registration}
        aria-invalid={error ? 'true' : 'false'}
        className="w-full h-10 px-3 border border-[var(--color-border)] rounded focus:outline-2 focus:outline-[#1C1C1E]"
      />
      {error && (
        <p role="alert" className="text-xs text-[#DC2626] mt-1">
          {error}
        </p>
      )}
    </div>
  );
}
