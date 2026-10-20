export interface OrganizationUnit {
  id: string;
  name: string;
  type: string;
  parentId?: string | null;
  active: boolean;
  allowedActions?: string[];
}
export interface UnitBranch {
  unit: OrganizationUnit;
  children: UnitBranch[];
}
export interface UnitTreeRow {
  unit: OrganizationUnit;
  depth: number;
  hasChildren: boolean;
  ancestors: string[];
  position: string;
}
