import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { baseTestProviders } from '../../../testing/test-providers';
import { ApplicationService } from './application.service';

describe('ApplicationService release calls', () => {
  let service: ApplicationService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: baseTestProviders() });
    service = TestBed.inject(ApplicationService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads the summary of a new version from the save answer', () => {
    let result: unknown;
    service
      .createApplicationVersion({
        applicationId: 1,
        version: '2',
        arch: null,
        releaseMode: 'immediate',
      })
      .subscribe((summary) => (result = summary));

    const request = http.expectOne('rest/private/applications/versions');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.releaseMode).toBe('immediate');
    request.flush({
      status: 'OK',
      message: null,
      data: { configurations: 2, devices: 5, mode: 'immediate' },
    });

    expect(result).toEqual({ configurations: 2, devices: 5, mode: 'immediate' });
  });

  it('turns an error envelope on upload into an error, not a summary', () => {
    let failure: Error | undefined;
    service
      .createApplicationVersion({ applicationId: 1, version: '2', arch: null })
      .subscribe({ error: (error) => (failure = error) });

    http
      .expectOne('rest/private/applications/versions')
      .flush({ status: 'ERROR', message: 'error.release.mode.invalid', data: null });

    expect(failure?.message).toBe('error.release.mode.invalid');
  });

  it('posts the chosen mode to the release endpoint', () => {
    let result: unknown;
    service.releaseApplicationVersion(9, 'schedule').subscribe((summary) => (result = summary));

    const request = http.expectOne('rest/private/applications/versions/9/release');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ mode: 'schedule' });
    request.flush({
      status: 'OK',
      message: null,
      data: { configurations: 0, devices: 0, mode: 'schedule' },
    });

    expect(result).toEqual({ configurations: 0, devices: 0, mode: 'schedule' });
  });

  it('turns a release error envelope into an error', () => {
    let failure: Error | undefined;
    service.releaseApplicationVersion(9, 'schedule').subscribe({ error: (e) => (failure = e) });

    http
      .expectOne('rest/private/applications/versions/9/release')
      .flush({ status: 'ERROR', message: 'error.version.already.released', data: null });

    expect(failure?.message).toBe('error.version.already.released');
  });

  it('does not send the release mode when creating an application', () => {
    service
      .createApplication({
        type: 'app',
        name: 'x',
        showIcon: false,
        arch: null,
        pkg: 'p',
        version: '1',
        runAfterInstall: false,
        runAtBoot: false,
        system: false,
        releaseMode: 'hold',
      } as never)
      .subscribe();

    const request = http.expectOne('rest/private/applications/android');
    expect('releaseMode' in request.request.body).toBeFalse();
    request.flush({ status: 'OK', message: null, data: {} });
  });

  it('posts the chosen mode to the notify endpoint and reads the full summary', () => {
    let result: unknown;
    service.notifyApplicationVersion(9, 'immediate').subscribe((summary) => (result = summary));

    const request = http.expectOne('rest/private/applications/versions/9/notify');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ mode: 'immediate' });
    request.flush({
      status: 'OK',
      message: null,
      data: { configurations: 2, devices: 5, mode: 'immediate', pushFailed: 1, notLatest: false },
    });

    expect(result).toEqual({
      configurations: 2,
      devices: 5,
      mode: 'immediate',
      pushFailed: 1,
      notLatest: false,
    });
  });

  it('surfaces error.version.exists from an upload as an error', () => {
    let failure: Error | undefined;
    service
      .createApplicationVersion({ applicationId: 1, version: '2', arch: null })
      .subscribe({ error: (error) => (failure = error) });

    http
      .expectOne('rest/private/applications/versions')
      .flush({ status: 'ERROR', message: 'error.version.exists', data: null });

    expect(failure?.message).toBe('error.version.exists');
  });
});
