import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, finalize, firstValueFrom, of, shareReplay, tap } from 'rxjs';
import { AuthResponse, UserInfo } from '../models';

/**
 * M4: Access token stored strictly in memory (signals), never in localStorage.
 * Session persistence across reloads uses an HttpOnly SameSite=Strict refresh cookie.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly _token = signal<string | null>(null);
  private readonly _user = signal<UserInfo | null>(null);
  private refreshInFlight$: Observable<AuthResponse | null> | null = null;

  readonly token = this._token.asReadonly();
  readonly user = this._user.asReadonly();
  readonly isLoggedIn = computed(() => !!this._token() && !!this._user());
  readonly isAdmin = computed(() => this._user()?.role === 'ADMIN');
  readonly isApprovedCreator = computed(() => this._user()?.creatorStatus === 'APPROVED');
  readonly unreadNotifications = computed(() => this._user()?.unreadNotifications ?? 0);

  bootstrapSession(): Promise<void> {
    // Clean up any legacy localStorage keys from older builds
    localStorage.removeItem('tone_token');
    localStorage.removeItem('tone_user');
    localStorage.removeItem('toneguitar_token');
    localStorage.removeItem('toneguitar_user');

    return firstValueFrom(
      this.refreshAccessToken().pipe(catchError(() => of(null)))
    ).then(() => undefined);
  }

  register(fullName: string, email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/register', { fullName, email, password }, { withCredentials: true })
      .pipe(tap(res => this.setSession(res)));
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/login', { email, password }, { withCredentials: true })
      .pipe(tap(res => this.setSession(res)));
  }

  refreshAccessToken(): Observable<AuthResponse | null> {
    if (!this.refreshInFlight$) {
      this.refreshInFlight$ = this.http
        .post<AuthResponse>('/api/auth/refresh', {}, { withCredentials: true })
        .pipe(
          tap(res => this.setSession(res)),
          catchError(() => {
            this.clearSession();
            return of(null);
          }),
          finalize(() => {
            this.refreshInFlight$ = null;
          }),
          shareReplay(1)
        );
    }
    return this.refreshInFlight$;
  }

  refreshMe(): Observable<UserInfo> {
    return this.http.get<UserInfo>('/api/auth/me').pipe(
      tap(user => this._user.set(user))
    );
  }

  logout(): void {
    this.http.post('/api/auth/logout', {}, { withCredentials: true }).subscribe({ error: () => {} });
    this.clearSession();
  }

  logoutAll(): Observable<void> {
    return this.http.post<void>('/api/auth/logout-all', {}, { withCredentials: true }).pipe(
      tap(() => this.clearSession())
    );
  }

  clearSession(): void {
    this._token.set(null);
    this._user.set(null);
  }

  private setSession(res: AuthResponse): void {
    this._token.set(res.token);
    this._user.set(res.user);
  }
}
