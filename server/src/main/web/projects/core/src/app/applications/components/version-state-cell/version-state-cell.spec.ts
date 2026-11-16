import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { signal } from '@angular/core';
import { baseTestProviders, useEnglish } from '../../../testing/test-providers';
import { ReleaseUserNamesService } from '../../services/release-user-names.service';
import { VersionStateCell } from './version-state-cell';

describe('VersionStateCell', () => {
  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [VersionStateCell],
      providers: [
        ...baseTestProviders(),
        {
          provide: ReleaseUserNamesService,
          useValue: {
            names: signal(new Map()),
            load: () => undefined,
            nameOf: (id: number) => (id === 4 ? 'Ana' : null),
          },
        },
      ],
    });
    await useEnglish(TestBed.inject(TranslateService));
  });

  function text(data: object): string {
    const fixture = TestBed.createComponent(VersionStateCell);
    fixture.componentRef.setInput('params', { data });
    fixture.detectChanges();
    return fixture.nativeElement.textContent.replace(/\s+/g, ' ').trim();
  }

  it('shows Held for a held version, without who or when', () => {
    expect(text({ releaseState: 'held', releasedAt: null, releasedBy: null })).toBe('Held');
  });

  it('shows Released with when and who for a released version', () => {
    const at = new Date(2026, 10, 16, 9, 30).getTime();

    expect(text({ releaseState: 'released', releasedAt: at, releasedBy: 4 })).toBe(
      'Released 2026-11-16 09:30 by Ana',
    );
  });

  it('falls back to the user id when the name is not available', () => {
    const at = new Date(2026, 10, 16, 9, 30).getTime();

    expect(text({ releaseState: 'released', releasedAt: at, releasedBy: 9 })).toContain('by #9');
  });
});
