import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

interface Registration { customerId: string; displayName: string; username: string; accountStatus: 'PENDING' | 'READY'; }
interface Login { accessToken: string; tokenType: string; expiresIn: number; }
interface Account { accountNumber: string; displayName: string; balanceCents: number; currency: 'BRL'; }
interface Recipient { accountNumber: string; displayName: string; }
interface Entry { operationId: string; type: string; amountCents: number; balanceAfterCents: number; occurredAt: string; description: string; }
interface Receipt { transferId: string; requestId: string; sourceAccountNumber: string; sourceDisplayName: string; destinationAccountNumber: string; destinationDisplayName: string; amountCents: number; currency: string; completedAt: string; }

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html'
})
export class AppComponent {
  private http = inject(HttpClient);
  mode: 'login' | 'register' = 'login';
  displayName = '';
  username = '';
  password = '';
  token = '';
  me?: Registration;
  account?: Account;
  entries: Entry[] = [];
  recipientNumber = '';
  recipient?: Recipient;
  amount = '';
  receipt?: Receipt;
  requestId?: string;
  busy = false;
  checking = false;
  message = '';

  format(cents: number): string {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(cents / 100);
  }

  private headers(): HttpHeaders { return new HttpHeaders({ Authorization: `Bearer ${this.token}` }); }
  private error(error: unknown): string {
    if (error instanceof HttpErrorResponse) return error.error?.message || `Solicitação falhou (${error.status || 'sem resposta'}).`;
    return 'Não foi possível concluir a solicitação.';
  }

  async register(): Promise<void> {
    this.busy = true; this.message = '';
    try {
      const result = await firstValueFrom(this.http.post<Registration>('/api/identity/register', {
        displayName: this.displayName, username: this.username, password: this.password
      }));
      this.mode = 'login';
      this.message = result.accountStatus === 'READY' ? 'Cadastro concluído. Entre na sua conta.' : 'Cadastro recebido. Sua conta está sendo preparada; você já pode entrar.';
    } catch (error) { this.message = this.error(error); }
    finally { this.busy = false; }
  }

  async login(): Promise<void> {
    this.busy = true; this.message = '';
    try {
      const login = await firstValueFrom(this.http.post<Login>('/api/identity/login', { username: this.username, password: this.password }));
      this.token = login.accessToken;
      this.password = '';
      await this.refresh();
    } catch (error) { this.message = this.error(error); }
    finally { this.busy = false; }
  }

  logout(): void {
    this.token = ''; this.me = undefined; this.account = undefined; this.entries = [];
    this.recipient = undefined; this.receipt = undefined; this.requestId = undefined; this.message = '';
  }

  async refresh(): Promise<void> {
    try {
      this.me = await firstValueFrom(this.http.get<Registration>('/api/identity/me', { headers: this.headers() }));
      if (this.me.accountStatus === 'PENDING') {
        this.account = undefined; this.message = 'Sua conta ainda está sendo preparada. Atualize em instantes.'; return;
      }
      this.account = await firstValueFrom(this.http.get<Account>('/api/finance/account', { headers: this.headers() }));
      this.entries = await firstValueFrom(this.http.get<Entry[]>('/api/finance/account/statement', { headers: this.headers() }));
      this.message = '';
    } catch (error) { this.message = this.error(error); }
  }

  async findRecipient(): Promise<void> {
    if (this.requestId) return;
    this.recipient = undefined; this.receipt = undefined; this.message = '';
    try {
      this.recipient = await firstValueFrom(this.http.get<Recipient>(`/api/finance/recipients/${encodeURIComponent(this.recipientNumber)}`, { headers: this.headers() }));
      if (this.recipient.accountNumber === this.account?.accountNumber) { this.recipient = undefined; this.message = 'Use outra conta como destino.'; }
    } catch (error) { this.message = this.error(error); }
  }

  async transfer(): Promise<void> {
    if (!this.recipient || this.busy) return;
    const normalized = this.amount.trim().replace(',', '.');
    if (!/^\d+(\.\d{1,2})?$/.test(normalized)) { this.message = 'Informe um valor válido com até duas casas decimais.'; return; }
    const [whole, fraction = ''] = normalized.split('.');
    const amountCents = Number(whole) * 100 + Number(fraction.padEnd(2, '0'));
    if (!Number.isSafeInteger(amountCents) || amountCents <= 0 || (!this.requestId && amountCents > (this.account?.balanceCents ?? 0))) {
      this.message = 'Valor inválido ou saldo insuficiente.'; return;
    }
    this.busy = true; this.message = '';
    this.requestId ??= crypto.randomUUID();
    try {
      this.receipt = await firstValueFrom(this.http.post<Receipt>('/api/finance/transfers', {
        requestId: this.requestId, destinationAccountNumber: this.recipient.accountNumber, amountCents
      }, { headers: this.headers() }));
      this.requestId = undefined;
      this.amount = '';
      await this.refresh();
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status >= 400 && error.status < 500) {
        this.requestId = undefined;
        this.message = this.error(error);
      } else {
        await this.checkResult();
      }
    } finally { this.busy = false; }
  }

  async checkResult(): Promise<void> {
    if (!this.requestId) return;
    this.checking = true;
    this.message = 'Verificando o resultado da mesma tentativa…';
    for (let attempt = 0; attempt < 10; attempt++) {
      try {
        this.receipt = await firstValueFrom(this.http.get<Receipt>(`/api/finance/transfers/${this.requestId}`, { headers: this.headers() }));
        this.requestId = undefined;
        this.amount = '';
        await this.refresh();
        this.checking = false;
        return;
      } catch (error) {
        if (error instanceof HttpErrorResponse && error.status === 401) { this.message = this.error(error); break; }
      }
      await new Promise(resolve => setTimeout(resolve, 2000));
    }
    this.checking = false;
    this.message = 'Resultado ainda desconhecido. Use “Verificar novamente” antes de fazer outra tentativa.';
  }

  print(): void { window.print(); }
}
