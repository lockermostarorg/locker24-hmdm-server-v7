import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import auditBs from '../../../../../../../../../plugins/audit/src/main/web/projects/audit/public/i18n/bs_BA.json';
import auditEn from '../../../../../../../../../plugins/audit/src/main/web/projects/audit/public/i18n/en_US.json';
import deviceinfoBs from '../../../../../../../../../plugins/deviceinfo/src/main/web/projects/deviceinfo/public/i18n/bs_BA.json';
import deviceinfoEn from '../../../../../../../../../plugins/deviceinfo/src/main/web/projects/deviceinfo/public/i18n/en_US.json';
import devicelogBs from '../../../../../../../../../plugins/devicelog/src/main/web/projects/devicelog/public/i18n/bs_BA.json';
import devicelogEn from '../../../../../../../../../plugins/devicelog/src/main/web/projects/devicelog/public/i18n/en_US.json';
import healthBs from '../../../../../../../../../plugins/locker24-health/src/main/web/projects/locker24-health/public/i18n/bs_BA.json';
import healthEn from '../../../../../../../../../plugins/locker24-health/src/main/web/projects/locker24-health/public/i18n/en_US.json';
import messagingBs from '../../../../../../../../../plugins/messaging/src/main/web/projects/messaging/public/i18n/bs_BA.json';
import messagingEn from '../../../../../../../../../plugins/messaging/src/main/web/projects/messaging/public/i18n/en_US.json';
import pushBs from '../../../../../../../../../plugins/push/src/main/web/projects/push/public/i18n/bs_BA.json';
import pushEn from '../../../../../../../../../plugins/push/src/main/web/projects/push/public/i18n/en_US.json';
import { LANGUAGE_OPTIONS } from '../settings/const/language-options.const';
import { AVAILABLE_LANGUAGES } from '../shared/const/available-languages.const';
import {
  detectBrowserLanguage,
  extractLanguageCode,
  LanguageService,
} from '../shared/services/language.service';
import { LANGUAGE_STORAGE_KEY } from '../shared/const/available-languages.const';
import { SettingsFacadeService } from '../shared/services/settings-facade.service';
import { THEME_STORAGE } from '../shared/services/theme.service';
import { TSettingsDTO } from '../entity/settings/types/settings-dto.type';
import { baseTestProviders, loadLocale, LOCALES } from './test-providers';

type TLocale = Record<string, string>;

const placeholders = (value: string): string[] => (value.match(/\{\{[^{}]*\}\}?/g) ?? []).sort();

function expectSameShape(name: string, en: TLocale, bs: TLocale): void {
  expect(Object.keys(bs)).withContext(`${name}: key order`).toEqual(Object.keys(en));

  for (const key of Object.keys(en)) {
    expect(typeof bs[key])
      .withContext(`${name}: ${key}`)
      .toBe('string');
    expect(bs[key].length > 0)
      .withContext(`${name}: ${key} is empty`)
      .toBe(en[key].length > 0);
    expect(placeholders(bs[key]))
      .withContext(`${name}: placeholders of ${key}`)
      .toEqual(placeholders(en[key]));
  }
}

describe('Bosnian locale', () => {
  it('has every core key of en_US with the same placeholders', async () => {
    expectSameShape('core', await loadLocale('en_US'), await loadLocale('bs_BA'));
  });

  const plugins: [string, TLocale, TLocale][] = [
    ['audit', auditEn, auditBs],
    ['deviceinfo', deviceinfoEn, deviceinfoBs],
    ['devicelog', devicelogEn, devicelogBs],
    ['messaging', messagingEn, messagingBs],
    ['push', pushEn, pushBs],
    ['locker24-health', healthEn, healthBs],
  ];

  for (const [name, en, bs] of plugins) {
    it(`has every ${name} plugin key of en_US with the same placeholders`, () => {
      expectSameShape(name, en, bs);
    });
  }

  it('is registered as an available language and a settings option', () => {
    expect(AVAILABLE_LANGUAGES).toContain('bs_BA');
    expect(LANGUAGE_OPTIONS).toContain(
      jasmine.objectContaining({ value: 'bs_BA', viewValue: 'form.settings.lang.bs' }),
    );
  });

  it('only lists languages that have a locale file', () => {
    expect([...AVAILABLE_LANGUAGES].sort()).toEqual([...LOCALES].sort());
    expect(LANGUAGE_OPTIONS.map((o) => o.value).sort()).toEqual([...LOCALES].sort());
  });

  for (const locale of LOCALES) {
    it(`labels Bosnian in ${locale}`, async () => {
      expect((await loadLocale(locale))['form.settings.lang.bs']).toBe('Bosanski');
    });
  }
});

describe('extractLanguageCode', () => {
  const cases: [string, string][] = [
    ['bs', 'bs_BA'],
    ['bs-BA', 'bs_BA'],
    ['bs-Latn-BA', 'bs_BA'],
    ['hr', 'bs_BA'],
    ['hr-HR', 'bs_BA'],
    ['hr-BA', 'bs_BA'],
    ['sr', 'bs_BA'],
    ['sr-Latn-RS', 'bs_BA'],
    ['sr-Cyrl-BA', 'bs_BA'],
    ['sh', 'bs_BA'],
    ['sh-BA', 'bs_BA'],
    ['bs_BA', 'bs_BA'],
    ['en-GB', 'en_US'],
    ['de-AT', 'de_DE'],
    ['pt-BR', 'pt_PT'],
    ['zh-CN', 'zh_CN'],
    ['zh-TW', 'zh_TW'],
    ['zh_CN', 'zh_CN'],
    ['fi-FI', 'en_US'],
    ['', 'en_US'],
  ];

  for (const [input, expected] of cases) {
    it(`maps '${input}' to ${expected}`, () => {
      expect(extractLanguageCode(input)).toBe(expected);
    });
  }
});

describe('detectBrowserLanguage', () => {
  it('shows Bosnian for Bosnian, Croatian and Serbian browsers', () => {
    for (const lang of ['bs', 'bs-BA', 'hr', 'hr-HR', 'sr', 'sr-Latn-RS', 'sr-Cyrl-BA', 'sh']) {
      expect(detectBrowserLanguage([lang]))
        .withContext(lang)
        .toBe('bs_BA');
    }
  });

  it('shows English for every other browser language', () => {
    for (const lang of ['en-US', 'en-GB', 'de-AT', 'fr-FR', 'zh-TW', 'sl-SI', 'fi-FI']) {
      expect(detectBrowserLanguage([lang]))
        .withContext(lang)
        .toBe('en_US');
    }
    expect(detectBrowserLanguage([])).toBe('en_US');
    expect(detectBrowserLanguage([undefined, ''])).toBe('en_US');
  });

  it('takes the first Bosnian-family or English entry of the browser list', () => {
    expect(detectBrowserLanguage(['sl-SI', 'hr-HR', 'en-US'])).toBe('bs_BA');
    expect(detectBrowserLanguage(['de-DE', 'en-US', 'bs-BA'])).toBe('en_US');
    expect(detectBrowserLanguage(['en-US', 'bs-BA'])).toBe('en_US');
  });
});

describe('LanguageService plugin translations', () => {
  let http: HttpTestingController;
  let translate: TranslateService;

  beforeEach(() => {
    (window as any)['__DYNPLUGINS__'] = [{ baseUrl: '/plugin-x' }];
    TestBed.configureTestingModule({ providers: baseTestProviders() });
    TestBed.inject(LanguageService);
    http = TestBed.inject(HttpTestingController);
    translate = TestBed.inject(TranslateService);
  });

  afterEach(() => {
    delete (window as any)['__DYNPLUGINS__'];
    http.verify();
  });

  it('loads the plugin file of the current language', () => {
    translate.use('bs_BA');

    http.expectOne('/plugin-x/i18n/bs_BA.json').flush({ 'plugin.x.title': 'Naslov' });

    expect(translate.instant('plugin.x.title')).toBe('Naslov');
  });

  it('falls back to the English plugin file when the language file is missing', () => {
    translate.use('bs_BA');

    http
      .expectOne('/plugin-x/i18n/bs_BA.json')
      .flush('not found', { status: 404, statusText: 'Not Found' });
    http.expectOne('/plugin-x/i18n/en_US.json').flush({ 'plugin.x.title': 'Title' });

    expect(translate.instant('plugin.x.title')).toBe('Title');
  });

  it('stays quiet when even the English plugin file is missing', () => {
    translate.use('bs_BA');

    http
      .expectOne('/plugin-x/i18n/bs_BA.json')
      .flush('not found', { status: 404, statusText: 'Not Found' });
    http
      .expectOne('/plugin-x/i18n/en_US.json')
      .flush('not found', { status: 404, statusText: 'Not Found' });

    expect(translate.instant('plugin.x.title')).toBe('plugin.x.title');
  });
});

describe('LanguageService before sign-in', () => {
  afterEach(() => {
    delete (window as any)['__DYNPLUGINS__'];
  });

  it('uses the browser language while no settings are loaded', () => {
    spyOnProperty(Object.getPrototypeOf(navigator), 'languages').and.returnValue([
      'sl-SI',
      'hr-HR',
    ]);
    spyOnProperty(Object.getPrototypeOf(navigator), 'language').and.returnValue('sl-SI');
    TestBed.configureTestingModule({ providers: baseTestProviders() });
    TestBed.inject(LanguageService);

    TestBed.tick();

    expect(TestBed.inject(TranslateService).getCurrentLang()).toBe('bs_BA');
  });
});

describe('LanguageService header choice', () => {
  let store: Record<string, string>;

  function setup(browser: string[], stored?: string) {
    store = stored ? { [LANGUAGE_STORAGE_KEY]: stored } : {};
    spyOnProperty(Object.getPrototypeOf(navigator), 'languages').and.returnValue(browser);
    spyOnProperty(Object.getPrototypeOf(navigator), 'language').and.returnValue(browser[0]);
    TestBed.configureTestingModule({
      providers: [
        ...baseTestProviders(),
        {
          provide: THEME_STORAGE,
          useValue: {
            getItem: (key: string) => store[key] ?? null,
            setItem: (key: string, value: string) => (store[key] = value),
          },
        },
      ],
    });
    const service = TestBed.inject(LanguageService);
    TestBed.tick();
    return { service, translate: TestBed.inject(TranslateService) };
  }

  afterEach(() => {
    delete (window as any)['__DYNPLUGINS__'];
  });

  it('keeps the chosen language over the browser language', () => {
    const { translate } = setup(['hr-HR'], 'en_US');

    expect(translate.getCurrentLang()).toBe('en_US');
  });

  it('ignores a stored value that is not a panel language', () => {
    const { translate } = setup(['hr-HR'], 'de_DE');

    expect(translate.getCurrentLang()).toBe('bs_BA');
  });

  it('switches, remembers the choice and exposes it', () => {
    const { service, translate } = setup(['en-US']);

    service.setLanguage('bs_BA');

    expect(translate.getCurrentLang()).toBe('bs_BA');
    expect(service.language()).toBe('bs_BA');
    expect(store[LANGUAGE_STORAGE_KEY]).toBe('bs_BA');
  });

  it('refuses languages the header does not offer', () => {
    const { service, translate } = setup(['en-US']);

    service.setLanguage('de_DE');

    expect(translate.getCurrentLang()).toBe('en_US');
    expect(store[LANGUAGE_STORAGE_KEY]).toBeUndefined();
  });

  it('keeps the chosen language when the settings ask for a fixed one', () => {
    const { translate } = setup(['en-US'], 'bs_BA');

    TestBed.inject(SettingsFacadeService).settings.set({
      useDefaultLanguage: false,
      language: 'en_US',
    } as TSettingsDTO);
    TestBed.tick();

    expect(translate.getCurrentLang()).toBe('bs_BA');
  });

  it('uses the fixed settings language when nothing was chosen', () => {
    const { translate } = setup(['hr-HR']);

    TestBed.inject(SettingsFacadeService).settings.set({
      useDefaultLanguage: false,
      language: 'en_US',
    } as TSettingsDTO);
    TestBed.tick();

    expect(translate.getCurrentLang()).toBe('en_US');
  });
});
