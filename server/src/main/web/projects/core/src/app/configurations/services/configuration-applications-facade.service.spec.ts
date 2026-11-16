import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApplicationService } from '../../entity/application/services/application.service';
import { TApplicationDTO } from '../../entity/application/types/application-dto.type';
import { TVersionDTO } from '../../entity/application/types/version-dto.type';
import { ConfigurationService } from '../../entity/configuration/services/configuration.service';
import { TConfigurationDTO } from '../../entity/configuration/types/configuration-dto.type';
import { baseTestProviders } from '../../testing/test-providers';
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

  function setup(apps: TApplicationDTO[], config: Partial<TConfigurationDTO> = {}): void {
    const configurationService = jasmine.createSpyObj('ConfigurationService', [
      'getConfigurationApps',
      'getConfigurationsById',
    ]);
    configurationService.getConfigurationApps.and.returnValue(of(apps));
    configurationService.getConfigurationsById.and.returnValue(
      of({ id: 5, name: 'Cfg', ...config } as TConfigurationDTO),
    );
    const applicationService = jasmine.createSpyObj('ApplicationService', [
      'search',
      'getVersionsByApplicationId',
    ]);
    applicationService.search.and.returnValue(of([]));
    applicationService.getVersionsByApplicationId.and.returnValue(of([V3_HELD, V2, V1]));

    TestBed.configureTestingModule({
      providers: [
        ...baseTestProviders(),
        { provide: ConfigurationService, useValue: configurationService },
        { provide: ApplicationService, useValue: applicationService },
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
});
