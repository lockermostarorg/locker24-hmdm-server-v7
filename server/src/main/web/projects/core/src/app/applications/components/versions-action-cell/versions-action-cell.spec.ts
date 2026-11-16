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
      'openNotifyVersionDialog',
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

  it('shows Notify tablets again only on released versions, next to the other actions', () => {
    const held = render({ releaseState: 'held' });
    const released = render({ releaseState: 'released' });

    const icons = (f: ComponentFixture<VersionsActionCell>): string[] =>
      Array.from<HTMLElement>(f.nativeElement.querySelectorAll('mat-icon')).map((el) =>
        el.textContent!.trim(),
      );

    expect(icons(released)).toContain('notifications');
    expect(icons(released)).not.toContain('publish');
    expect(icons(held)).toContain('publish');
    expect(icons(held)).not.toContain('notifications');
  });

  it('opens the notify dialog for the row', () => {
    const fixture = render({ releaseState: 'released', version: '3' });

    fixture.componentInstance.onNotifyClick();

    expect(dialogService.openNotifyVersionDialog).toHaveBeenCalledWith(
      jasmine.objectContaining({ id: 5, version: '3' }),
    );
  });
});
