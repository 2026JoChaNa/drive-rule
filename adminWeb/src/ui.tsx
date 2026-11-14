import { useEffect, useId, useRef, type ReactNode } from 'react';
import { AlertCircle, X, Inbox, LoaderCircle } from 'lucide-react';
import { ApiError } from './api';
import { statusLabels, triageLabels, requestLabels } from './types';

export function Badge({ value, children }: { value: string; children?: ReactNode }) { return <span className={`badge badge-${value.toLowerCase()}`}>{children || ({ ...statusLabels, ...triageLabels, ...requestLabels } as Record<string, string>)[value] || value}</span>; }
export function ErrorBox({ error }: { error: unknown }) { if (!error) return null; return <div className="error-box" role="alert"><AlertCircle size={18}/><div><strong>{error instanceof Error ? error.message : String(error)}</strong>{error instanceof ApiError && <><small>{error.code}</small>{error.errors.length > 0 && <ul>{error.errors.map((e, i) => <li key={i}>{typeof e === 'string' ? e : e.field + ': ' + e.message}</li>)}</ul>}</>}</div></div>; }
export function Empty({ title = '아직 등록된 항목이 없습니다', text = '새 항목을 등록하면 이곳에 표시됩니다.' }: { title?: string; text?: string }) { return <div className="empty"><div className="empty-icon"><Inbox size={25}/></div><h3>{title}</h3><p>{text}</p></div>; }
export function Spinner() { return <LoaderCircle className="spin" size={16}/>; }
export function Field({ label, children, hint, className = '' }: { label: string; children: ReactNode; hint?: string; className?: string }) { return <label className={`field ${className}`}><span>{label}</span>{children}{hint && <small>{hint}</small>}</label>; }
export function Modal({ title, subtitle, children, onClose, wide = false }: { title: string; subtitle?: string; children: ReactNode; onClose: () => void; wide?: boolean }) {
  const ref = useRef<HTMLDialogElement>(null); const titleId = useId();
  useEffect(() => { const dialog = ref.current; const previous = document.activeElement as HTMLElement | null; dialog?.showModal(); return () => { dialog?.close(); previous?.focus(); }; }, []);
  return <dialog ref={ref} className={`modal ${wide ? 'modal-wide' : ''}`} aria-labelledby={titleId} onCancel={e => { e.preventDefault(); onClose(); }}><div className="modal-head"><div><h2 id={titleId}>{title}</h2>{subtitle && <p>{subtitle}</p>}</div><button type="button" className="icon-button" aria-label="닫기" onClick={onClose}><X size={21}/></button></div>{children}</dialog>;
}
export function dateLabel(value: string | null | undefined, time = false) { if (!value) return '—'; return value.slice(0, time ? 16 : 10).replace('T', ' ').replaceAll('-', '.'); }
