const form=document.querySelector('#scan-form');
const input=document.querySelector('#address');
const output=document.querySelector('#output');
const radar=document.querySelector('#target');
form.addEventListener('submit',event=>{
 event.preventDefault();
 const raw=input.value.trim();
 if(!raw){output.textContent='Enter a web address first.';return;}
 let address;
 try{address=new URL(/^[a-z][a-z0-9+.-]*:\/\//i.test(raw)?raw:'https://'+raw);}
 catch{output.textContent='This address could not be read. Check it and try again.';return;}
 if(!['http:','https:'].includes(address.protocol)){output.textContent='Only normal web addresses can be reviewed.';return;}
 const host=address.hostname.toLowerCase();
 const warnings=[];
 if(address.protocol!=='https:')warnings.push('The address does not use HTTPS.');
 if(address.username||address.password)warnings.push('The address contains user information before the hostname.');
 if(host.startsWith('xn--')||host.includes('.xn--'))warnings.push('The hostname uses punycode; check for lookalike characters.');
 if(host.split('.').length>4)warnings.push('The hostname has many nested parts.');
 if(host.length>55)warnings.push('The hostname is unusually long.');
 if(/^\\d{1,3}(\\.\\d{1,3}){3}$/.test(host))warnings.push('The address uses an IP number instead of a domain name.');
 if(/login|verify|wallet|secure|update|claim|bonus|support/i.test(host))warnings.push('The hostname contains words often used in imitation or urgency lures.');
 radar.textContent=host;
 output.textContent=(warnings.length?warnings.join(' '):'No obvious URL-structure warning was found by this basic check.')+' This local check does not contact threat databases, follow redirects, or prove a site is safe or unsafe.';
});
