// Writes a copy of the JRE's javaw.exe whose version info and icon say
// Renzo Hub. Task Manager names a process by its exe's FileDescription, so
// running the app through this copy shows "Renzo Hub" instead of
// "OpenJDK Platform binary". The java launcher finds its runtime from its
// own folder, so the copy must sit next to javaw.exe in jre\bin.
// Usage: node brand-javaw.mjs <javaw.exe> <out.exe> <icon.ico>
// Needs resedit + pe-library importable (build-windows-exe.sh installs them).
import fs from 'node:fs';
import * as PE from 'pe-library';
import * as ResEdit from 'resedit';

const [src, out, ico] = process.argv.slice(2);
// ignoreCert: javaw carries Adoptium's signature, which an edit voids
// anyway; the build signs the result with the Hub's own certificate.
const exe = PE.NtExecutable.from(fs.readFileSync(src), { ignoreCert: true });
const res = PE.NtExecutableResource.from(exe);

const [vi] = ResEdit.Resource.VersionInfo.fromEntries(res.entries);
if (!vi) throw new Error(`${src} has no version info`);
const names = {
  FileDescription: 'Renzo Hub',
  ProductName: 'Renzo Hub',
  CompanyName: 'Levitate Media',
  InternalName: 'RenzoHub',
  OriginalFilename: 'RenzoHub.exe',
};
for (const lang of vi.getAllLanguagesForStringValues()) vi.setStringValues(lang, names);
vi.outputToResourceEntries(res.entries);

const groups = ResEdit.Resource.IconGroupEntry.fromEntries(res.entries);
const icons = ResEdit.Data.IconFile.from(fs.readFileSync(ico)).icons.map((i) => i.data);
const [g] = groups;
ResEdit.Resource.IconGroupEntry.replaceIconsForResource(res.entries, g ? g.id : 1, g ? g.lang : 1033, icons);

res.outputResource(exe);
fs.writeFileSync(out, Buffer.from(exe.generate()));

// Self-check: read the written file back.
const check = ResEdit.Resource.VersionInfo.fromEntries(
  PE.NtExecutableResource.from(PE.NtExecutable.from(fs.readFileSync(out))).entries)[0];
const lang = check.getAllLanguagesForStringValues()[0];
const desc = check.getStringValues(lang).FileDescription;
if (desc !== 'Renzo Hub') throw new Error(`branding did not stick: ${desc}`);
console.log(`branded ${out}`);
