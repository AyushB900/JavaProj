const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const base=process.env.BANKSYNC_WEB_URL||'http://127.0.0.1:18080';
const fs=require('node:fs');const path=require('node:path');
if(!['localhost','127.0.0.1','[::1]'].includes(new URL(base).hostname))throw new Error('Browser smoke checks only support a disposable local demo.');
const artifactDir=process.env.BANKSYNC_ARTIFACT_DIR||path.resolve('target/browser');fs.mkdirSync(artifactDir,{recursive:true});
(async()=>{
 const browser=await chromium.launch({...(process.env.BROWSER_EXECUTABLE ? {executablePath:process.env.BROWSER_EXECUTABLE} : {}),headless:true,args:['--no-sandbox']});
 try{
 const page=await browser.newPage({viewport:{width:1440,height:1100}});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto(base,{waitUntil:'networkidle'});
 const initial=await (await page.request.get(base+'/api/v1/dashboard')).json();
 assert.equal(initial.environment,'Demo workspace','Use a disposable demo profile, never a production database');
 assert.equal(initial.transactionCount,0,'Restart the disposable demo before running this mutating smoke test');
 await page.getByText('Workspace connected',{exact:true}).waitFor();
 assert.equal(await page.locator('#total-balance').innerText(),'$1,500.00');
 await page.getByRole('button',{name:'New transfer',exact:true}).click();
 await page.locator('#transfer-amount').fill('25.50');
 await page.getByRole('button',{name:'Review transfer'}).click();
 await page.getByRole('button',{name:'Confirm transfer'}).click();
 await page.locator('#transfer-dialog').waitFor({state:'hidden'});
 await page.getByText('ACC-1001',{exact:false}).first().waitFor();
 await page.waitForFunction(()=>document.querySelector('#transfer-count').textContent==='1');
 assert.equal(await page.locator('#total-balance').innerText(),'$1,500.00');
 assert.match(await page.locator('#account-grid').innerText(),/974.50/);
 assert.match(await page.locator('#transaction-rows').innerText(),/25.50/);
 await page.getByRole('button',{name:'New transfer',exact:true}).click();
 await page.locator('#transfer-amount').fill('9999');
 await page.getByRole('button',{name:'Review transfer'}).click();
 assert.match(await page.locator('#transfer-error').innerText(),/exceeds the available balance/);
 await page.locator('#transfer-dialog').getByRole('button',{name:'Cancel',exact:true}).click();
 // Populate real committed history to exercise server-side pagination, not mock rows.
 for(let i=0;i<11;i++){
  const r=await page.request.post(base+'/api/v1/transfers',{data:{fromAccountNumber:'ACC-1002',toAccountNumber:'ACC-1001',amount:'1.00'}});assert.equal(r.status(),204);
 }
 await page.getByRole('button',{name:'Refresh balances and activity'}).click();
 await page.waitForFunction(()=>document.querySelector('#history-count').textContent==='12');
 await page.getByRole('button',{name:'Next page',exact:true}).click();
 await page.waitForFunction(()=>document.querySelector('#page-number').textContent==='Page 2 of 2');
 assert.equal(await page.locator('#transaction-rows tr').count(),2);
 const downloadPromise=page.waitForEvent('download');await page.getByRole('button',{name:'Export page',exact:true}).click();
 const download=await downloadPromise;assert.match(download.suggestedFilename(),/page-2\.csv$/);
 const stream=await download.createReadStream();let csv='';for await(const chunk of stream)csv+=chunk.toString();assert.match(csv,/Amount \(USD\)/);assert.equal(csv.trim().split('\r\n').length,3);
 await page.getByRole('searchbox').fill('NOT-FOUND');await page.getByText('No matching transfers',{exact:true}).waitFor();
 await page.getByRole('searchbox').fill('ACC-1001');await page.waitForFunction(()=>document.querySelector('#history-count').textContent==='12');
 await page.getByRole('link',{name:/Accounts/}).click();await page.waitForFunction(()=>document.querySelector('h1').textContent.includes('home for every balance'));assert.match(await page.locator('h1').innerText(),/home for every balance/);
 assert.equal(await page.locator('#transactions-section').isVisible(),false);
 await page.getByRole('link',{name:'Overview',exact:true}).click();await page.waitForFunction(()=>document.querySelector('h1').textContent.includes('Your money'));
 await page.locator('#toast').evaluate(el=>el.classList.add('hidden'));await page.screenshot({path:path.join(artifactDir,'banksync-desktop.png'),fullPage:true});
 await page.setViewportSize({width:390,height:844});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),true,'Mobile page must not overflow horizontally');
 await page.getByRole('button',{name:'Open navigation',exact:true}).click();await page.getByRole('link',{name:'Activity',exact:true}).click();
 await page.waitForFunction(()=>document.querySelector('h1').textContent.includes('Every move'));assert.match(await page.locator('h1').innerText(),/Every move/);
 await page.getByRole('button',{name:'New transfer',exact:true}).click();assert.equal(await page.locator('#transfer-dialog').isVisible(),true);
 await page.keyboard.press('Escape');assert.equal(await page.locator('#transfer-dialog').isVisible(),false);
 await page.getByRole('button',{name:'Open navigation',exact:true}).click();await page.getByRole('link',{name:'Overview',exact:true}).click();await page.waitForFunction(()=>document.querySelector('h1').textContent.includes('Your money'));
 await page.locator('#sidebar').evaluate(el=>Promise.all(el.getAnimations().map(a=>a.finished)));await page.mouse.move(380,830);await page.screenshot({path:path.join(artifactDir,'banksync-mobile.png'),fullPage:true});
 let attempts=0;await page.route('**/api/v1/transfers',route=>{attempts++;return route.abort('failed');});
 await page.getByRole('button',{name:'New transfer',exact:true}).click();await page.locator('#transfer-amount').fill('1.00');await page.getByRole('button',{name:'Review transfer'}).click();await page.getByRole('button',{name:'Confirm transfer'}).click();await page.waitForFunction(()=>document.querySelector('#transfer-error').textContent.includes('outcome could not be confirmed'));assert.equal(await page.locator('#submit-transfer').isDisabled(),true);assert.equal(attempts,1);await page.locator('#transfer-dialog').getByRole('button',{name:'Cancel',exact:true}).click();
 // API failures must produce an explicit error, rather than an empty, successful-looking table.
 await page.route('**/api/v1/transactions?**',route=>route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({message:'Activity temporarily unavailable'})}));
 await page.getByRole('button',{name:'Refresh balances and activity'}).click();await page.getByText('Activity could not be loaded',{exact:true}).waitFor();
 assert.equal(await page.getByRole('button',{name:'Export page',exact:true}).isDisabled(),true);
 assert.deepEqual(errors,[]);
 console.log('PASS: real transfer, exact balances, insufficient funds, history pagination/search, CSV download, navigation, mobile layout, accessible dialog, interrupted transfer without automatic retry, API failure state; no browser exceptions.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
