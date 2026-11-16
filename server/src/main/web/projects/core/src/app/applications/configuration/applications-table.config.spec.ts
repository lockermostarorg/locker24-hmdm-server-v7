import { ApplicationsTableConfig } from './applications-table.config';

describe('ApplicationsTableConfig', () => {
  it('shows the package in the Package ID column with the default renderer', () => {
    const pkg = new ApplicationsTableConfig().getConfig().columns.find((c) => c.field === 'pkg')!;

    expect(pkg.cellRenderer).toBeUndefined();
  });
});
