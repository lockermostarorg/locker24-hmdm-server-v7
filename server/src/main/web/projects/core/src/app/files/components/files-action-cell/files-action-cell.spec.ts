import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../../shared/services/auth.service';
import { baseTestProviders, useEnglish } from '../../../testing/test-providers';
import { FilesDialogService } from '../../services/files-dialog.service';
import { FilesActionCell } from './files-action-cell';

describe('FilesActionCell', () => {
  const file = (id: number, url: string) => ({
    id,
    url,
    size: 10,
    usedByApps: false,
    usedByConfigurations: [],
    usedByIcons: [],
  });

  it('follows the row when params switch to another row', async () => {
    const dialogs = jasmine.createSpyObj('FilesDialogService', ['openEditFileDialog']);
    TestBed.configureTestingModule({
      imports: [FilesActionCell],
      providers: [
        ...baseTestProviders(),
        { provide: FilesDialogService, useValue: dialogs },
        { provide: AuthService, useValue: { hasPermission: () => true, currentUser: signal(null) } },
      ],
    });
    await useEnglish(TestBed.inject(TranslateService));
    const fixture = TestBed.createComponent(FilesActionCell);
    fixture.componentRef.setInput('params', { data: file(1, 'https://x.test/a') });
    fixture.detectChanges();
    expect(fixture.componentInstance.copyTooltip()).toBe('https://x.test/a');

    fixture.componentRef.setInput('params', { data: file(2, 'https://x.test/b') });
    fixture.detectChanges();

    expect(fixture.componentInstance.copyTooltip()).toBe('https://x.test/b');
    fixture.componentInstance.onEditClick();
    expect(dialogs.openEditFileDialog).toHaveBeenCalledWith(jasmine.objectContaining({ id: 2 }));
  });
});
