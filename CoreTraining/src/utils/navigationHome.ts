import type { NavigationItem } from '../api/navigationApi';

export function navigationHome(items:NavigationItem[]):string {
  const admin=items.filter(item=>item.area==='ADMIN'&&item.visible);
  if(admin.some(item=>item.id==='admin.overview'))return '/admin';
  // Report views are drawn by the admin layout, so their entries are hidden; a report-only role starts on its first view.
  const report=items.filter(item=>item.area==='ADMIN'&&item.id.startsWith('admin.reports.')).sort((a,b)=>a.sortOrder-b.sortOrder)[0];
  return admin[0]?.route||report?.route||'/';
}
