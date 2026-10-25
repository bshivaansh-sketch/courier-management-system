/* Runs before styles paint so a saved dark preference never flashes light. */
(()=>{
  const key='parcelly-theme',root=document.documentElement;
  const system=window.matchMedia?.('(prefers-color-scheme: dark)');
  let saved;try{saved=localStorage.getItem(key);}catch{}
  let preference=['light','dark'].includes(saved)?saved:null;
  let theme=preference||(system?.matches?'dark':'light'),timer;
  function sync(){
    document.querySelectorAll('[data-theme-toggle]').forEach(button=>{
      button.setAttribute('aria-pressed',String(theme==='dark'));
      button.setAttribute('aria-label',theme==='dark'?'Switch to light theme':'Switch to dark theme');
      button.title=theme==='dark'?'Switch to light theme':'Switch to dark theme';
      const label=button.querySelector('.theme-label');if(label)label.textContent=theme==='dark'?'Light mode':'Dark mode';
    });
  }
  function apply(next,animate=false){
    theme=next;
    if(animate){clearTimeout(timer);root.classList.add('theme-switching');timer=setTimeout(()=>root.classList.remove('theme-switching'),260);}
    root.dataset.theme=theme;root.style.colorScheme=theme;sync();
  }
  apply(theme);
  window.ParcellyTheme={current:()=>theme,sync};
  document.addEventListener('click',event=>{
    if(!event.target.closest('[data-theme-toggle]'))return;
    preference=theme==='dark'?'light':'dark';
    try{localStorage.setItem(key,preference);}catch{}
    apply(preference,true);
  });
  system?.addEventListener?.('change',event=>{if(!preference)apply(event.matches?'dark':'light',true);});
  window.addEventListener('storage',event=>{
    if(event.key!==key)return;
    preference=['light','dark'].includes(event.newValue)?event.newValue:null;
    apply(preference||(system?.matches?'dark':'light'),true);
  });
})();
