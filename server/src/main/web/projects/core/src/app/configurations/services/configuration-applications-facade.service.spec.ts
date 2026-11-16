import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { ApplicationService } from '../../entity/application/services/application.service';
import { TApplicationDTO } from '../../entity/application/types/application-dto.type';
import { TVersionDTO } from '../../entity/application/types/version-dto.type';
import { ConfigurationService } from '../../entity/configuration/services/configuration.service';
import { TConfigurationDTO } from '../../entity/configuration/types/configuration-dto.type';
import { AuthService } from '../../shared/services/auth.service';
import { baseTestProviders } from '../../testing/test-providers';
import { provideRouter } from '@angular/router';
import { ConfigurationDetails } from '../pages/configuration-details/configuration-details';
import { ConfigurationAppsFacadeService } from './configuration-applications-facade.service';
import { ConfigurationDetailsFacadeService } from './configuration-details-facade.service';

const version = (id: number, name: string, code: number, held = false): TVersionDTO =>
  ({
    id,
    applicationId: 1,
    version: name,
    versionCode: code,
    url: `https://x/${name}.apk`,
    urlArmeabi: null,
    urlArm64: null,
    split: false,
    releaseState: held ? 'held' : 'released',
  }) as TVersionDTO;

const V1 = version(10, '1.0', 100);
const V2 = version(11, '2.0', 200);
const V3_HELD = version(12, '3.0', 300, true);

const row = (patch: Partial<TApplicationDTO> = {}): TApplicationDTO =>
  ({
    id: 1,
    pkg: 'app.demo',
    name: 'Demo',
    type: 'app',
    system: false,
    action: 1,
    usedVersionId: 11,
    latestVersion: 11,
    latestVersionText: '2.0',
    version: '2.0',
    versionCode: 200,
    outdated: false,
    followLatest: true,
    ...patch,
  }) as TApplicationDTO;

describe('ConfigurationAppsFacadeService release flow', () => {
  let facade: ConfigurationAppsFacadeService;
  let details: ConfigurationDetailsFacadeService;

  let applicationService: jasmine.SpyObj<ApplicationService>;
  let configurationService: jasmine.SpyObj<ConfigurationService>;
  let canLoadVersions = true;

  function setup(apps: TApplicationDTO[], config: Partial<TConfigurationDTO> = {}): void {
    configurationService = jasmine.createSpyObj('ConfigurationService', [
      'getConfigurationApps',
      'getConfigurationsById',
    ]);
    canLoadVersions = true;
    configurationService.getConfigurationApps.and.returnValue(of(apps));
    configurationService.getConfigurationsById.and.returnValue(
      of({ id: 5, name: 'Cfg', ...config } as TConfigurationDTO),
    );
    applicationService = jasmine.createSpyObj('ApplicationService', [
      'search',
      'getVersionsByApplicationId',
    ]);
    applicationService.search.and.returnValue(of([]));
    applicationService.getVersionsByApplicationId.and.returnValue(of([V3_HELD, V2, V1]));

    TestBed.configureTestingModule({
      providers: [
        ...baseTestProviders(),
        provideRouter([]),
        { provide: ConfigurationService, useValue: configurationService },
        { provide: ApplicationService, useValue: applicationService },
        { provide: AuthService, useValue: { hasPermission: () => canLoadVersions } },
      ],
    });
    facade = TestBed.inject(ConfigurationAppsFacadeService);
    details = TestBed.inject(ConfigurationDetailsFacadeService);
    details.setCurrentConfigurationId(5);
    TestBed.tick();
  }

  const current = (): TApplicationDTO => facade.applications()[0];

  it('pins the row when a version is picked', () => {
    setup([row()]);

    facade.setAppVersion(current(), V1);

    expect(current().followLatest).toBeFalse();
    expect(current().usedVersionId).toBe(10);
    expect(current().version).toBe('1.0');
  });

  it('marks a row pinned behind the latest as outdated', () => {
    setup([row()]);
    facade.getVersionOptions(current()).subscribe();

    facade.setAppVersion(current(), V1);

    expect(current().outdated).toBeTrue();
  });

  it('does not mark a row pinned to a held version ahead of the latest as outdated', () => {
    setup([row()]);
    facade.getVersionOptions(current()).subscribe();

    facade.setAppVersion(current(), V3_HELD);

    expect(current().outdated).toBeFalse();
    expect(current().followLatest).toBeFalse();
  });

  it('lists held versions in the picker', () => {
    setup([row()]);
    let options: TVersionDTO[] = [];

    facade.getVersionOptions(current()).subscribe((versions) => (options = versions));

    expect(options.map((v) => v.version)).toEqual(['3.0', '2.0', '1.0']);
  });

  it('follows the latest released version again when the toggle is turned on', () => {
    setup([row({ usedVersionId: 10, version: '1.0', versionCode: 100, followLatest: false })]);

    facade.setFollowLatest(current(), true);

    expect(current().followLatest).toBeTrue();
    expect(current().usedVersionId).toBe(11);
    expect(current().version).toBe('2.0');
    expect(current().outdated).toBeFalse();
  });

  it('keeps the row where it is when the toggle is turned off', () => {
    setup([row()]);

    facade.setFollowLatest(current(), false);

    expect(current().followLatest).toBeFalse();
    expect(current().usedVersionId).toBe(11);
  });

  it('sends followLatest=false for a picked version and true after the toggle is turned on', () => {
    setup([row()]);

    facade.setAppVersion(current(), V1);
    expect(facade.applications()[0].followLatest).toBe(false);

    facade.setFollowLatest(current(), true);
    expect(facade.applications()[0].followLatest).toBe(true);
  });

  it('adds a new row as following', () => {
    setup([]);

    facade.addApplication(row({ id: 2, action: 1, followLatest: null }));

    expect(facade.applications()[0].followLatest).toBeTrue();
  });

  describe('main and content app', () => {
    it('mirrors the install row of the main app', () => {
      setup([row()], { mainAppId: 11, followLatestMainApp: true });

      expect(facade.followsLatestMainApp()).toBeTrue();

      facade.setMainAppFollowsLatest(false);

      expect(current().followLatest).toBeFalse();
      expect(facade.followsLatestMainApp()).toBeFalse();
      expect(details.currentConfiguration()?.followLatestMainApp).toBeFalse();
    });

    it('pins the main app when a version is picked on its row', () => {
      setup([row()], { mainAppId: 11, followLatestMainApp: true });

      facade.setAppVersion(current(), V1);

      expect(details.currentConfiguration()?.mainAppId).toBe(10);
      expect(details.currentConfiguration()?.followLatestMainApp).toBeFalse();
    });

    it('moves the main app to the latest when following is turned back on', () => {
      setup([row({ usedVersionId: 10, followLatest: false })], {
        mainAppId: 10,
        followLatestMainApp: false,
      });

      facade.setMainAppFollowsLatest(true);

      expect(details.currentConfiguration()?.mainAppId).toBe(11);
      expect(details.currentConfiguration()?.followLatestMainApp).toBeTrue();
    });

    it('mirrors the content app in the same way', () => {
      setup([row()], { contentAppId: 11, followLatestContentApp: true });

      facade.setContentAppFollowsLatest(false);

      expect(facade.followsLatestContentApp()).toBeFalse();
      expect(details.currentConfiguration()?.followLatestContentApp).toBeFalse();
    });

    it('keeps the flag on the configuration when the app has no install row', () => {
      setup([row({ action: 0 })], { mainAppId: 11, followLatestMainApp: true });

      facade.setMainAppFollowsLatest(false);

      expect(facade.followsLatestMainApp()).toBeFalse();
      expect(details.currentConfiguration()?.followLatestMainApp).toBeFalse();
    });
  });

  describe('turning following on', () => {
    const pinned = (): TApplicationDTO =>
      row({ usedVersionId: 10, version: '1.0', versionCode: 100, followLatest: false });

    it('sets the flag, the version and the main flag before any fetch answers', () => {
      setup([pinned()], { mainAppId: 10, followLatestMainApp: false });
      const pending = new Subject<TVersionDTO[]>();
      applicationService.getVersionsByApplicationId.and.returnValue(pending);

      facade.setMainAppFollowsLatest(true);

      expect(current().followLatest).toBeTrue();
      expect(current().usedVersionId).toBe(11);
      expect(details.currentConfiguration()?.mainAppId).toBe(11);
      expect(details.currentConfiguration()?.followLatestMainApp).toBeTrue();
    });

    it('still follows when the versions request fails', () => {
      setup([pinned()]);
      applicationService.getVersionsByApplicationId.and.returnValue(
        throwError(() => new Error('network')),
      );

      facade.setFollowLatest(current(), true);

      expect(current().followLatest).toBeTrue();
      expect(current().usedVersionId).toBe(11);
    });

    it('still follows when the versions answer is null, and caches nothing', () => {
      setup([pinned()]);
      applicationService.getVersionsByApplicationId.and.returnValue(
        of(null as unknown as TVersionDTO[]),
      );

      facade.setFollowLatest(current(), true);

      expect(current().followLatest).toBeTrue();
      let options: TVersionDTO[] = [{} as TVersionDTO];
      facade.getVersionOptions(current()).subscribe((versions) => (options = versions));
      expect(options).toEqual([]);
      expect(applicationService.getVersionsByApplicationId).toHaveBeenCalledTimes(2);
    });

    it('does not ask for versions without the applications permission', () => {
      setup([pinned()]);
      canLoadVersions = false;

      facade.setFollowLatest(current(), true);

      expect(applicationService.getVersionsByApplicationId).not.toHaveBeenCalled();
      expect(current().followLatest).toBeTrue();
    });

    it('fills the row text from the versions list when it arrives', () => {
      setup([pinned()]);
      applicationService.getVersionsByApplicationId.and.returnValue(of([V2, V1]));

      facade.setFollowLatest(current(), true);

      expect(current().url).toBe('https://x/2.0.apk');
      expect(current().versionCode).toBe(200);
    });

    it('does not leave the hidden latest entry next to the row', () => {
      const twin = row({ action: 0, usedVersionId: 11, version: '2.0', followLatest: null });
      setup([pinned(), twin]);

      facade.setFollowLatest(facade.applications()[0], true);

      expect(facade.applications().length).toBe(1);
    });
  });

  describe('selector change', () => {
    const other = row({ id: 2, pkg: 'app.other', usedVersionId: 21, latestVersion: 21 });

    it('follows when the new app has a following install row', () => {
      setup([row(), other], { contentAppId: 11, followLatestContentApp: false });

      facade.syncFollowLatestFlagWithSelection('content', 21);

      expect(details.currentConfiguration()?.followLatestContentApp).toBeTrue();
    });

    it('is pinned when the new app has a pinned row', () => {
      setup([row(), { ...other, followLatest: false }], {
        contentAppId: 11,
        followLatestContentApp: true,
      });

      facade.syncFollowLatestFlagWithSelection('content', 21);

      expect(details.currentConfiguration()?.followLatestContentApp).toBeFalse();
    });

    it('follows without an install row when the chosen version is the latest released', () => {
      setup([row(), { ...other, action: 0 }], { mainAppId: 11, followLatestMainApp: false });

      facade.syncFollowLatestFlagWithSelection('main', 21);

      expect(details.currentConfiguration()?.followLatestMainApp).toBeTrue();
    });

    it('is pinned without an install row when the chosen version is not the latest', () => {
      setup([row(), { ...other, action: 0, usedVersionId: 20, latestVersion: 21 }], {
        mainAppId: 11,
        followLatestMainApp: true,
      });

      facade.syncFollowLatestFlagWithSelection('main', 20);

      expect(details.currentConfiguration()?.followLatestMainApp).toBeFalse();
    });
  });

  describe('save body', () => {
    let saved: TConfigurationDTO;

    function save(): void {
      configurationService.updateConfiguration = jasmine
        .createSpy('updateConfiguration')
        .and.callFake((body: TConfigurationDTO) => {
          saved = body;
          return of(null);
        });
      const component = TestBed.runInInjectionContext(() => new ConfigurationDetails());
      component.onSave();
    }

    const flags = (): (boolean | null | undefined)[] =>
      (saved.applications as TApplicationDTO[]).map((a) => a.followLatest);

    it('carries false for a picked version and the pinned main flag', () => {
      setup([row()], { mainAppId: 11, followLatestMainApp: true });
      facade.setAppVersion(current(), V1);

      save();

      expect(flags()).toEqual([false]);
      expect(saved.mainAppId).toBe(10);
      expect(saved.followLatestMainApp).toBeFalse();
    });

    it('carries true after toggling on, even if the versions fetch never answers', () => {
      setup([row({ usedVersionId: 10, followLatest: false })], {
        mainAppId: 10,
        followLatestMainApp: false,
      });
      applicationService.getVersionsByApplicationId.and.returnValue(new Subject());

      facade.setMainAppFollowsLatest(true);
      save();

      expect(flags()).toEqual([true]);
      expect(saved.followLatestMainApp).toBeTrue();
      expect(saved.mainAppId).toBe(11);
    });

    it('carries true after toggling on when the versions fetch fails', () => {
      setup([row({ usedVersionId: 10, followLatest: false })]);
      applicationService.getVersionsByApplicationId.and.returnValue(
        throwError(() => new Error('x')),
      );

      facade.setFollowLatest(current(), true);
      save();

      expect(flags()).toEqual([true]);
    });

    it('sends one row per app after toggling on', () => {
      setup([
        row({ usedVersionId: 10, followLatest: false }),
        row({ action: 0, usedVersionId: 11, followLatest: null }),
      ]);

      facade.setFollowLatest(facade.applications()[0], true);
      save();

      expect((saved.applications as TApplicationDTO[]).length).toBe(1);
    });

    it('carries true for an added row', () => {
      setup([]);
      facade.addApplication(row({ id: 3, followLatest: null }));

      save();

      expect(flags()).toEqual([true]);
    });

    it('echoes the stored flag of untouched rows', () => {
      setup([row({ followLatest: false }), row({ id: 4, pkg: 'x', followLatest: true })]);

      save();

      expect(flags()).toEqual([false, true]);
    });
  });
});
