import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

interface Registration { customerId: string; displayName: string; username: string; accountStatus: 'PENDING' | 'READY'; }
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

  async register(): Promise<void> { throw new Error('TODO: implementar cadastro'); }
  async login(): Promise<void> { throw new Error('TODO: implementar login'); }
  logout(): void { throw new Error('TODO: implementar saída'); }
  async refresh(): Promise<void> { throw new Error('TODO: implementar atualização da conta'); }
  async findRecipient(): Promise<void> { throw new Error('TODO: implementar consulta de destinatário'); }
  async transfer(): Promise<void> { throw new Error('TODO: implementar transferência'); }
  async checkResult(): Promise<void> { throw new Error('TODO: implementar consulta da transferência'); }
  print(): void { window.print(); }
}
