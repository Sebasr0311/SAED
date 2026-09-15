import fs from 'fs';
import path from 'path';

const pagesDir = path.resolve('frontend/src/pages');
const files = fs.readdirSync(pagesDir).filter((f) => f.endsWith('.jsx'));

console.log(`Scanning ${files.length} pages in ${pagesDir}...\n`);

const findings = [];

for (const file of files) {
  const filePath = path.join(pagesDir, file);
  const content = fs.readFileSync(filePath, 'utf-8');
  const lines = content.split('\n');

  lines.forEach((line, idx) => {
    const lineNum = idx + 1;

    // Pattern 1: parseInt(e.target.value) or parseFloat(e.target.value) without || or isNaN
    if (/parse(Int|Float)\s*\(\s*e\.target\.value\s*\)/.test(line)) {
      if (!line.includes('||') && !line.includes('isNaN') && !line.includes('?') && !line.includes('Number.isNaN')) {
        findings.push({
          file,
          lineNum,
          type: 'UNSAFE_PARSE_INT',
          snippet: line.trim(),
          risk: 'Direct parseInt on e.target.value yields NaN when input is empty or invalid',
        });
      }
    }

    // Pattern 2: Number(e.target.value) without fallback
    if (/Number\s*\(\s*e\.target\.value\s*\)/.test(line)) {
      if (!line.includes('||') && !line.includes('?') && !line.includes('0')) {
        findings.push({
          file,
          lineNum,
          type: 'UNSAFE_NUMBER_CAST',
          snippet: line.trim(),
          risk: 'Number("") is 0; Number("abc") is NaN without check',
        });
      }
    }

    // Pattern 3: input type="number" with direct string state
    if (line.includes('type="number"') || line.includes("type='number'")) {
      findings.push({
        file,
        lineNum,
        type: 'INPUT_TYPE_NUMBER',
        snippet: line.trim(),
      });
    }
  });
}

console.log(`Total unsafe parsing findings: ${findings.filter(f => f.type.startsWith('UNSAFE')).length}`);
console.log(`Total type="number" inputs: ${findings.filter(f => f.type === 'INPUT_TYPE_NUMBER').length}\n`);

console.log('--- DETAILED UNSAFE NUMBER CASTS ---');
for (const f of findings.filter(f => f.type.startsWith('UNSAFE'))) {
  console.log(`[${f.file}:${f.lineNum}] ${f.type}:`);
  console.log(`   ${f.snippet}`);
  console.log(`   Risk: ${f.risk}\n`);
}
