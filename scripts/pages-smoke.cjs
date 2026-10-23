'use strict';
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const base=process.env.BANKSYNC_PAGES_URL||'http://127.0.0.1:18083/docs/';
if(!['localhost','127.0.0.1','[::1]'].includes(new URL(base).hostname))throw new Error('Use a local static copy for the Pages smoke check.');
(async()=>{
 const browser=await chromium.launch({...(process.env.BROWSER_EXECUTABLE?{executablePath:process.env.BROWSER_EXECUTABLE}:{}),headless:true,args:['--no-sandbox']});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000}});
  const errors=[],apiRequests=[],failedAssets=[];
  page.on('pageerror',e=>errors.push(e.message));
  page.on('request',r=>{if(new URL(r.url()).pathname.startsWith('/api/'))apiRequests.push(r.url());});
  page.on('response',r=>{if(r.status()>=400)failedAssets.push(r.url());});
  await page.goto(base,{waitUntil:'networkidle'});
  await page.waitForFunction(()=>document.querySelector('#connection-status').textContent==='Browser demo');
  assert.match(await page.locator('.site-demo-banner').innerText(),/No real money moves/);
  assert.equal(await page.locator('#total-balance').innerText(),'$25,000.00');
  assert.equal(await page.locator('#transfer-count').innerText(),'8');
  await page.getByRole('button',{name:'New transfer',exact:true}).click();
  await page.locator('#transfer-amount').fill('10.25');
  await page.getByRole('button',{name:'Review transfer'}).click();
  await page.getByRole('button',{name:'Confirm transfer'}).click();
  await page.waitForFunction(()=>document.querySelector('#transfer-count').textContent==='9');
  assert.equal(await page.locator('#total-balance').innerText(),'$25,000.00');
  assert.match(await page.locator('#transaction-rows').innerText(),/10.25/);
  const download=page.waitForEvent('download');await page.getByRole('button',{name:'Export page',exact:true}).click();
  assert.match((await download).suggestedFilename(),/\.csv$/);
  await page.getByRole('searchbox').fill('NOT-FOUND');await page.getByText('No matching transfers',{exact:true}).waitFor();
  await page.reload({waitUntil:'networkidle'});
  await page.waitForFunction(()=>document.querySelector('#transfer-count').textContent==='8');
  assert.equal(await page.locator('#total-balance').innerText(),'$25,000.00');
  await page.setViewportSize({width:390,height:844});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),true);
  assert.deepEqual(apiRequests,[],'Static demo must never call a banking API');
  assert.deepEqual(failedAssets,[],'All site assets must load at the project subpath');
  assert.deepEqual(errors,[]);
  console.log('PASS: GitHub Pages subpath assets, visible demo label, simulated transfer, exact totals, export, search, reload reset and mobile layout; no API calls or browser errors.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
