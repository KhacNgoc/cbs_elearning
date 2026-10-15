import type { NavigationItem } from '../api/navigationApi';

export function navigationHome(items:NavigationItem[]):string {
  const admin=items.filter(item=>item.area==='ADMIN'&&item.visible);
  return admin.some(item=>item.id==='admin.overview')?'/admin':admin[0]?.route||'/';
}
