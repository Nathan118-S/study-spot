// Dependency-free Excel export using SpreadsheetML 2003 (.xls).
// Produces a real multi-sheet Excel workbook that Excel/Numbers/LibreOffice open natively.

function escapeXml(s) {
  if (s == null) return '';
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function cell(value) {
  if (value == null || value === '') return '<Cell><Data ss:Type="String"></Data></Cell>';
  if (typeof value === 'number' && !Number.isNaN(value)) {
    return `<Cell><Data ss:Type="Number">${value}</Data></Cell>`;
  }
  if (typeof value === 'boolean') {
    return `<Cell><Data ss:Type="Boolean">${value ? 1 : 0}</Data></Cell>`;
  }
  return `<Cell><Data ss:Type="String">${escapeXml(value)}</Data></Cell>`;
}

export function downloadExcelXml(sheets, filename = 'export.xls') {
  const worksheets = sheets
    .map((sheet) => {
      const header = `<Row>${sheet.columns.map((c) => cell(c.label)).join('')}</Row>`;
      const body = sheet.rows
        .map((row) => `<Row>${sheet.columns.map((col) => cell(row[col.key])).join('')}</Row>`)
        .join('');
      return `<Worksheet ss:Name="${escapeXml(sheet.name)}"><Table>${header}${body}</Table></Worksheet>`;
    })
    .join('\n');

  const xml =
    `<?xml version="1.0"?>\n` +
    `<?mso-application progid="Excel.Sheet"?>\n` +
    `<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"\n` +
    ` xmlns:o="urn:schemas-microsoft-com:office:office"\n` +
    ` xmlns:x="urn:schemas-microsoft-com:office:excel"\n` +
    ` xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">\n` +
    `${worksheets}\n</Workbook>`;

  const blob = new Blob([xml], { type: 'application/vnd.ms-excel' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}