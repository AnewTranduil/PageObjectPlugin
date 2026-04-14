import { type Page, type Locator } from '@playwright/test';

/**
 * Dashboard page — a richer fixture that exercises external CSS
 * references (bundled as sidecars under `resources/` when captured via
 * the live-capture path).
 */
export class DashboardPage {
  readonly heading: Locator;
  readonly newProjectButton: Locator;
  readonly exportButton: Locator;
  readonly projectsTable: Locator;
  readonly ticketForm: Locator;
  readonly ticketTitleInput: Locator;
  readonly ticketPrioritySelect: Locator;
  readonly ticketDescription: Locator;
  readonly ticketSubmit: Locator;

  constructor(private readonly page: Page) {
    this.heading = page.getByRole('heading', { name: 'Welcome back, Amelia' });
    this.newProjectButton = page.getByTestId('new-project-button');
    this.exportButton = page.getByTestId('export-button');
    this.projectsTable = page.getByTestId('projects-table');
    this.ticketForm = page.getByTestId('ticket-form');
    this.ticketTitleInput = page.getByTestId('ticket-title');
    this.ticketPrioritySelect = page.getByTestId('ticket-priority');
    this.ticketDescription = page.getByTestId('ticket-description');
    this.ticketSubmit = page.getByTestId('ticket-submit');
  }

  async goto() {
    await this.page.goto('http://localhost:8089/app.html');
  }

  async fillTicket(title: string, priority: 'low' | 'medium' | 'high' | 'critical', description: string) {
    await this.ticketTitleInput.fill(title);
    await this.ticketPrioritySelect.selectOption(priority);
    await this.ticketDescription.fill(description);
  }
}
