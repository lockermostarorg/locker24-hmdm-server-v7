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
import { extractLanguageCode, LanguageService } from '../shared/services/language.service';
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
    spyOnProperty(Object.getPrototypeOf(navigator), 'language').and.returnValue('hr-HR');
    TestBed.configureTestingModule({ providers: baseTestProviders() });
    TestBed.inject(LanguageService);

    TestBed.tick();

    expect(TestBed.inject(TranslateService).getCurrentLang()).toBe('bs_BA');
  });
});
