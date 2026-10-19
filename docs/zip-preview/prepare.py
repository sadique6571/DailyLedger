from pathlib import Path
import base64,zipfile,io,shutil,re
payload=''.join(p.read_text() for p in sorted(Path('docs/zip-preview').glob('source-*.b64')))
for p in ['app/src/main','app/src/androidTest','app/src/test']:
 shutil.rmtree(p,ignore_errors=True)
with zipfile.ZipFile(io.BytesIO(base64.b64decode(payload))) as z:z.extractall('.')
shutil.rmtree('app/src/androidTest',ignore_errors=True)
p=Path('app/src/androidTest/java/com/sadique/dailyledger/ui/ZipUiPreviewTest.kt');p.parent.mkdir(parents=True,exist_ok=True);p.write_text(Path('docs/zip-preview/ZipUiPreviewTest.kt').read_text())
# The ZIP has unresolved imports; add imports only, without changing visuals.
base=Path('app/src/main/java/com/sadique/dailyledger/ui/screens')
for rel,imp in [('CategoryRow.kt','androidx.compose.foundation.layout.fillMaxWidth'),('dashboard/DashboardSpendingChart.kt','androidx.compose.ui.draw.clip'),('kameti/KametiDialogs.kt','androidx.compose.ui.Alignment'),('transactions/TransactionList.kt','androidx.compose.material.icons.outlined.DeleteOutline')]:
 p=base/rel;s=p.read_text()
 if ('import '+imp+'\n') not in s:p.write_text(re.sub(r'(package [^\n]+\n)',lambda m:m.group(1)+'import '+imp+'\n',s,count=1))
