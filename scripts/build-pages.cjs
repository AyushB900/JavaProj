'use strict';
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),source=path.join(root,'src/main/resources/static'),output=path.join(root,'docs');
let index=fs.readFileSync(path.join(source,'index.html'),'utf8');
index=index.replace('  <script src="./app.js" defer></script>','  <script src="./demo.js" defer></script>\n  <script src="./app.js" defer></script>');
index=index.replace('Overview · BankSync','BankSync · Interactive demo');
const files=new Map([['index.html',index],['demo.js',fs.readFileSync(path.join(__dirname,'pages-demo.js'),'utf8')],['.nojekyll','']]);
for(const file of ['app.js','styles.css','favicon.svg'])files.set(file,fs.readFileSync(path.join(source,file),'utf8'));
if(process.argv.includes('--check')){
 for(const [name,content]of files)if(!fs.existsSync(path.join(output,name))||fs.readFileSync(path.join(output,name),'utf8')!==content)throw new Error(`docs/${name} is out of date. Run npm run build:pages.`);
 console.log('GitHub Pages files match their source.');
}else{
 fs.mkdirSync(output,{recursive:true});for(const [name,content]of files)fs.writeFileSync(path.join(output,name),content);
 console.log('Built GitHub Pages browser demo in docs/.');
}
