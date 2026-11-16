import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TVersionDTO } from '../../../entity/application/types/version-dto.type';
import { AuthService } from '../../../shared/services/auth.service';
import { baseTestProviders } from '../../../testing/test-providers';
import { VersionDialogService } from '../../services/version-dialog.service';
import { VersionsActionCell } from './versions-action-cell';

describe('VersionsActionCell', () => {
  let dialogService: jasmine.SpyObj<VersionDialogService>;

  function render(version: Partial<TVersionDTO>): ComponentFixture<VersionsActionCell> {
    const fixture = TestBed.createComponent(VersionsActionCell);
    fixture.componentRef.setInput('params', { data: { id: 5, ...version } });
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    dialogService = jasmine.createSpyObj('VersionDialogService', [
      'openReleaseVersionDialog',
      'openEditVersionDialog',
    ]);
    TestBed.configureTestingModule({
      imports: [VersionsActionCell],
      providers: [
        ...baseTestProviders(),
        { provide: VersionDialogService, useValue: dialogService },
        { provide: AuthService, useValue: { hasPermission: () => true } },
      ],
    });
  });

  it('shows Release only on held versions', () => {
    const held = render({ releaseState: 'held' });
    const released = render({ releaseState: 'released' });

    expect(held.nativeElement.querySelectorAll('button').length).toBe(
      released.nativeElement.querySelectorAll('button').length + 1,
    );
    expect(held.componentInstance.isHeld()).toBeTrue();
    expect(released.componentInstance.isHeld()).toBeFalse();
  });

  it('opens the release dialog for the row', () => {
    const fixture = render({ releaseState: 'held', version: '3' });

    fixture.componentInstance.onReleaseClick();

    expect(dialogService.openReleaseVersionDialog).toHaveBeenCalledWith(
      jasmine.objectContaining({ id: 5, version: '3' }),
    );
  });
});
