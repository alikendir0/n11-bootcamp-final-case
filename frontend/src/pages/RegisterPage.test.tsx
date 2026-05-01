import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { registerRequest } from '../api/authApi';
import { ApiError } from '../lib/apiClient';
import RegisterPage from './RegisterPage';

vi.mock('../api/authApi', () => ({
  registerRequest: vi.fn(),
}));

const navigateMock = vi.fn();

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useNavigate: () => navigateMock,
  };
});

const mockedRegisterRequest = vi.mocked(registerRequest);

function renderRegisterPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function fillForm(password: string) {
  fireEvent.change(screen.getByLabelText('Ad Soyad'), { target: { value: 'Ayşe Yılmaz' } });
  fireEvent.change(screen.getByLabelText('E-posta'), { target: { value: 'ayse@example.com' } });
  fireEvent.change(screen.getByLabelText('Şifre'), { target: { value: password } });
  fireEvent.change(screen.getByLabelText('Şifre (tekrar)'), { target: { value: password } });
}

describe('RegisterPage', () => {
  beforeEach(() => {
    mockedRegisterRequest.mockReset();
    navigateMock.mockReset();
  });

  it('shows Turkish inline validation for passwords without a digit', async () => {
    renderRegisterPage();
    fillForm('sekizharf');

    fireEvent.click(screen.getByRole('button', { name: 'Üye Ol' }));

    expect(await screen.findByText('Şifre en az 8 karakter olmalı, en az bir harf ve bir rakam içermelidir.')).toBeVisible();
    expect(mockedRegisterRequest).not.toHaveBeenCalled();
  });

  it('shows Turkish inline validation for passwords shorter than eight characters', async () => {
    renderRegisterPage();
    fillForm('abc1');

    fireEvent.click(screen.getByRole('button', { name: 'Üye Ol' }));

    expect(await screen.findByText('Şifre en az 8 karakter olmalı, en az bir harf ve bir rakam içermelidir.')).toBeVisible();
    expect(mockedRegisterRequest).not.toHaveBeenCalled();
  });

  it('surfaces backend 400 problem details as visible form feedback', async () => {
    mockedRegisterRequest.mockRejectedValueOnce(
      new ApiError(400, { detail: 'Şifre en az 8 karakter, bir harf ve bir rakam içermelidir' }),
    );
    renderRegisterPage();
    fillForm('abc12345');

    fireEvent.click(screen.getByRole('button', { name: 'Üye Ol' }));

    await waitFor(() => expect(mockedRegisterRequest).toHaveBeenCalledTimes(1));
    expect(await screen.findByText('Şifre en az 8 karakter, bir harf ve bir rakam içermelidir')).toBeVisible();
  });
});
