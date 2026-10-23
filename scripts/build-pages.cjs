'use strict';
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),source=path.join(root,'src/main/resources/static');
let index=fs.readFileSync(path.join(source,'index.html'),'utf8');
index=index.replace('  <script src="./app.js" defer></script>','  <script src="./demo.js" defer></script>\n  <script src="./app.js" defer></script>');
index=index.replace('Overview · BankSync','BankSync · Interactive demo');
// Support both GitHub Pages source selections. A root index takes precedence over README rendering.
const rootIndex=index.replaceAll('href="./favicon.svg"','href="./docs/favicon.svg"')
  .replaceAll('href="./styles.css"','href="./docs/styles.css"')
  .replaceAll('src="./demo.js"','src="./docs/demo.js"')
  .replaceAll('src="./app.js"','src="./docs/app.js"');
const files=new Map([
 ['index.html',rootIndex],['.nojekyll',''],
 ['docs/index.html',index],['docs/demo.js',fs.readFileSync(path.join(__dirname,'pages-demo.js'),'utf8')],['docs/.nojekyll','']
]);
for(const file of ['app.js','styles.css','favicon.svg'])files.set('docs/'+file,fs.readFileSync(path.join(source,file),'utf8'));
if(process.argv.includes('--check')){
 for(const [name,content]of files)if(!fs.existsSync(path.join(root,name))||fs.readFileSync(path.join(root,name),'utf8')!==content)throw new Error(`${name} is out of date. Run npm run build:pages.`);
 console.log('Root and docs/ GitHub Pages websites match their source.');
}else{
 for(const [name,content]of files){fs.mkdirSync(path.dirname(path.join(root,name)),{recursive:true});fs.writeFileSync(path.join(root,name),content);}
 console.log('Built the GitHub Pages dashboard at the repository root and docs/.');
}
