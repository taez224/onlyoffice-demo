import React from 'react';

export const IconWord = ({ className }: { className?: string }) => (
  <svg width="100%" height="100%" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg" className={className}>
    <rect x="4" y="2" width="24" height="28" rx="2" fill="#2B579A"/>
    <rect x="20" y="2" width="8" height="8" fill="#ffffff" fillOpacity="0.2"/>
    <rect x="20" y="2" width="8" height="8" rx="1" fill="#ffffff" fillOpacity="0.1"/>
    <path d="M19 22L16.5 13H15.5L13 22H11L9.5 13H8L12 25H14L16 17L18 25H20L24 13H22.5L21 22H19Z" fill="white"/>
    <path d="M7 6H25" stroke="white" strokeOpacity="0.3" strokeWidth="2"/>
    <path d="M7 10H25" stroke="white" strokeOpacity="0.3" strokeWidth="2"/>
  </svg>
);

export const IconExcel = ({ className }: { className?: string }) => (
  <svg width="100%" height="100%" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg" className={className}>
    <rect x="4" y="2" width="24" height="28" rx="2" fill="#217346"/>
    <rect x="20" y="2" width="8" height="8" fill="#ffffff" fillOpacity="0.2"/>
    <path d="M11 13H13L16 18L19 13H21L17 19.5L21 25H19L16 21L13 25H11L15 19.5L11 13Z" fill="white"/>
    <rect x="8" y="7" width="4" height="2" fill="white" fillOpacity="0.3"/>
    <rect x="14" y="7" width="4" height="2" fill="white" fillOpacity="0.3"/>
    <rect x="20" y="7" width="4" height="2" fill="white" fillOpacity="0.3"/>
  </svg>
);

export const IconPPT = ({ className }: { className?: string }) => (
  <svg width="100%" height="100%" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg" className={className}>
    <rect x="4" y="2" width="24" height="28" rx="2" fill="#D24726"/>
    <rect x="20" y="2" width="8" height="8" fill="#ffffff" fillOpacity="0.2"/>
    <path d="M12 13H17C18.6569 13 20 14.3431 20 16C20 17.6569 18.6569 19 17 19H14V25H12V13ZM14 15V17H17C17.5523 17 18 16.5523 18 16C18 15.4477 17.5523 15 17 15H14Z" fill="white"/>
    <circle cx="23" cy="23" r="3" fill="white" fillOpacity="0.2"/>
  </svg>
);

export const IconPDF = ({ className }: { className?: string }) => (
  <svg width="100%" height="100%" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg" className={className}>
    <rect x="4" y="2" width="24" height="28" rx="2" fill="#E40000"/>
    <path d="M10 13H13.5C14.8807 13 16 14.1193 16 15.5V16.5C16 17.8807 14.8807 19 13.5 19H12V22H10V13ZM12 15V17H13.5C13.7761 17 14 16.7761 14 16.5V15.5C14 15.2239 13.7761 15 13.5 15H12Z" fill="white"/>
    <path d="M17 13H21C22.1046 13 23 13.8954 23 15V20C23 21.1046 22.1046 22 21 22H17V13ZM19 15V20H21V15H19Z" fill="white"/>
  </svg>
);
