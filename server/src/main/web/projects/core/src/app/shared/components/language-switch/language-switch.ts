import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { TranslatePipe } from '@ngx-translate/core';
import { PANEL_LANGUAGES } from '../../const/available-languages.const';
import { LanguageService } from '../../services/language.service';

@Component({
  selector: 'core-language-switch',
  templateUrl: './language-switch.html',
  styleUrl: './language-switch.scss',
  imports: [MatButtonModule, MatIconModule, MatMenuModule, TranslatePipe],
})
export class LanguageSwitch {
  private readonly languageService = inject(LanguageService);

  readonly options = PANEL_LANGUAGES;
  readonly current = this.languageService.language;
  readonly currentOption = computed(
    () => this.options.find((o) => o.code === this.current()) ?? null,
  );

  select(code: string): void {
    this.languageService.setLanguage(code);
  }
}
