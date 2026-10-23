/* GitHub Pages only: an explicitly labeled, in-memory simulation. No bank or database connection. */
'use strict';
(() => {
  const accounts = [
    {accountNumber:'ACC-1001', balance:1250000n},
    {accountNumber:'ACC-1002', balance:650000n},
    {accountNumber:'ACC-1003', balance:250000n},
    {accountNumber:'ACC-1004', balance:350000n}
  ];
  const transactions=[];
  const max=9999999999999999999n;
  const decimal=n=>`${n/100n}.${String(n%100n).padStart(2,'0')}`;
  function failure(message,status=400){const error=new Error(message);error.status=status;throw error;}
  function transfer(from,to,amount,timestamp=new Date().toISOString()){
    if(typeof amount!=='string'||!/^\d{1,17}(\.\d{1,2})?$/.test(amount))failure('Enter a positive amount with at most two decimal places.');
    const [whole,fraction='']=amount.split('.');const value=BigInt(whole)*100n+BigInt(fraction.padEnd(2,'0'));
    if(value<=0n||value>max)failure('The transfer amount must be positive and within the balance limit.');
    if(from===to)failure('Choose two different accounts.');
    const source=accounts.find(a=>a.accountNumber===from),destination=accounts.find(a=>a.accountNumber===to);
    if(!source||!destination)failure('One or both demo accounts do not exist.',404);
    if(source.balance<value)failure('Insufficient demo funds.',422);
    if(destination.balance+value>max)failure('Destination balance limit exceeded.');
    // One synchronous mutation in this tab. This does not simulate database concurrency guarantees.
    source.balance-=value;destination.balance+=value;
    transactions.push({id:String(transactions.length+1),sourceAccountNumber:from,destinationAccountNumber:to,amount:decimal(value),timestamp,status:'SUCCESS'});
  }
  const seeds=[['ACC-1001','ACC-1003','240.00',6],['ACC-1002','ACC-1001','500.00',5],['ACC-1001','ACC-1004','350.00',4],['ACC-1003','ACC-1001','65.00',3],['ACC-1001','ACC-1002','450.00',2],['ACC-1002','ACC-1004','125.00',1],['ACC-1001','ACC-1003','89.50',1],['ACC-1004','ACC-1001','75.00',0]];
  for(const [from,to,amount,daysAgo] of seeds){const date=new Date();date.setUTCDate(date.getUTCDate()-daysAgo);date.setUTCHours(10,30+transactions.length,0,0);if(date>new Date())date.setUTCHours(0,0,0,0);transfer(from,to,amount,date.toISOString());}
  const ordered=()=>[...transactions].sort((a,b)=>b.timestamp.localeCompare(a.timestamp)||Number(b.id)-Number(a.id));
  window.BankSyncDemo={
    async request(path,options={}){
      const url=new URL(path,location.origin);
      if(url.pathname==='/api/v1/dashboard')return {
        environment:'Browser demo',totalBalance:decimal(accounts.reduce((sum,a)=>sum+a.balance,0n)),
        totalTransferred:decimal(transactions.reduce((sum,t)=>{const [w,f]=t.amount.split('.');return sum+BigInt(w)*100n+BigInt(f);},0n)),
        accountCount:accounts.length,transactionCount:transactions.length,
        accounts:accounts.map(a=>({...a,balance:decimal(a.balance)})),recentTransactions:ordered().slice(0,100),refreshedAt:new Date().toISOString()
      };
      if(url.pathname==='/api/v1/transactions'){
        const page=Number(url.searchParams.get('page')||0),size=Number(url.searchParams.get('size')||10),search=url.searchParams.get('search')||'';
        if(!Number.isInteger(page)||page<0||!Number.isInteger(size)||size<1||size>50||! /^[A-Za-z0-9-]{0,64}$/.test(search))failure('Invalid search or page.');
        const rows=ordered().filter(t=>t.sourceAccountNumber.toLowerCase().includes(search.toLowerCase())||t.destinationAccountNumber.toLowerCase().includes(search.toLowerCase()));
        return {items:rows.slice(page*size,(page+1)*size),totalElements:rows.length,totalPages:Math.ceil(rows.length/size),page};
      }
      if(url.pathname==='/api/v1/transfers'&&options.method==='POST'){
        const data=JSON.parse(options.body);transfer(data.fromAccountNumber,data.toAccountNumber,data.amount);return null;
      }
      failure('This action is not available in the browser demo.',404);
    }
  };
  document.addEventListener('DOMContentLoaded',()=>{
    const banner=document.createElement('div');banner.className='site-demo-banner';banner.setAttribute('role','note');
    banner.innerHTML='<strong>Interactive demo</strong><span>Sample balances only. No real money moves. Changes reset when you reload.</span>';
    document.querySelector('main').prepend(banner);
    const about=document.querySelector('#about-dialog .modal-description');
    about.textContent='This GitHub Pages site is a browser-only simulation with sample USD balances. Transfers affect only this tab and reset on reload. It is not connected to a bank or the Java/PostgreSQL backend.';
    document.querySelector('#about-dialog .about-facts').innerHTML='<p><strong>Try it out</strong><br>Move sample funds, search activity and export a transaction page.</p><p><strong>About the full application</strong><br>The repository also contains the Java 21 and PostgreSQL service with transactional transfers and concurrency tests. Those database guarantees apply to the backend, not this simulation.</p><p><strong>Your demo stays in this tab</strong><br>No credentials are requested and no financial data is sent to a server. Reload to reset the sample data.</p>';
    document.querySelector('.sidebar-bottom strong').textContent='Explore a connected workspace.';
    document.querySelector('.sidebar-bottom p').innerHTML='Sample accounts.<br>A hands-on product demo.';
    document.querySelector('.quick-caption').lastChild.textContent=' Sample transfers. Real interface.';
    document.querySelector('.transfer-note').lastChild.textContent=' This simulates a transfer in your browser only.';
    document.querySelector('.tiny-shield').parentElement.lastChild.textContent='Sample transaction history';
    document.querySelector('.quick-panel .panel-heading p').textContent='Try a transfer with sample funds.';
    document.querySelector('#transfer-dialog .modal-description').textContent='Move sample funds between demo accounts in this tab.';
  });
})();
