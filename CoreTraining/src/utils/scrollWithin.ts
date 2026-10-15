/** Scroll one visible viewport only; never scroll hidden layout/animation ancestors. */
export function scrollWithin(target:HTMLElement|null|undefined, block:'start'|'nearest'='start', smooth=true) {
  if(!target)return;
  const win=target.ownerDocument.defaultView;
  if(!win)return;
  const behavior:ScrollBehavior=smooth&&!win.matchMedia('(prefers-reduced-motion: reduce)').matches?'smooth':'auto';
  const rect=target.getBoundingClientRect();
  const margin=parseFloat(win.getComputedStyle(target).scrollMarginTop)||0;
  for(let parent=target.parentElement;parent;parent=parent.parentElement){
    const overflow=win.getComputedStyle(parent).overflowY;
    if(!/^(auto|scroll)$/.test(overflow))continue;
    // An explicitly scrollable container owns this action even when its content fits.
    const top=parent.getBoundingClientRect().top+parent.clientTop;
    const bottom=top+parent.clientHeight;
    const delta=block==='start'?rect.top-top-margin
      :rect.top<top?rect.top-top-margin:rect.bottom>bottom?Math.min(rect.top-top,rect.bottom-bottom):0;
    if(delta)parent.scrollTo({top:parent.scrollTop+delta,behavior});
    return;
  }
  const delta=block==='start'?rect.top-margin:rect.top<0?rect.top:rect.bottom>win.innerHeight?Math.min(rect.top,rect.bottom-win.innerHeight):0;
  if(delta)win.scrollBy({top:delta,behavior});
}
