'use client'

import { useState } from 'react';
import Link from 'next/link';
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { 
  PlusIcon, 
  FileTextIcon,
  SearchIcon,
  FilterIcon,
  MoreHorizontal,
  FileText,          
  FileSpreadsheet,   
  Presentation,      
  FileType,          
  File,               
  Download,
  Trash2,
  Share2,
  X
} from "lucide-react";

export default function HomePage() {
  // Mock Data
  const documents = [
    { id: '1', name: 'Technical_Spec_v2.docx', type: 'DOCX', size: '2.4 MB', created: '2024-03-10 09:00', modified: '2024-03-20 14:30' },
    { id: '2', name: 'Q1_Financial_Report.xlsx', type: 'XLSX', size: '850 KB', created: '2024-03-12 11:30', modified: '2024-03-19 09:15' },
    { id: '3', name: 'Project_Alpha_Overview.pptx', type: 'PPTX', size: '12.5 MB', created: '2024-03-15 15:00', modified: '2024-03-18 16:45' },
    { id: '4', name: 'System_Architecture.pdf', type: 'PDF', size: '4.2 MB', created: '2024-03-01 08:20', modified: '2024-03-15 11:20' },
  ];

  // State for selection
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  // Selection handlers
  const toggleSelection = (id: string) => {
    setSelectedIds(prev => 
      prev.includes(id) ? prev.filter(item => item !== id) : [...prev, id]
    );
  };

  const toggleAll = () => {
    if (selectedIds.length === documents.length) {
      setSelectedIds([]);
    } else {
      setSelectedIds(documents.map(d => d.id));
    }
  };

  // Helper function to get icon
  const getFileIcon = (type: string) => {
    switch (type) {
      case 'DOCX': return <FileText className="text-blue-600 dark:text-blue-400" size={18} />;
      case 'XLSX': return <FileSpreadsheet className="text-emerald-600 dark:text-emerald-400" size={18} />;
      case 'PPTX': return <Presentation className="text-orange-600 dark:text-orange-400" size={18} />;
      case 'PDF':  return <FileType className="text-red-600 dark:text-red-400" size={18} />;
      default:     return <File className="text-muted-foreground" size={18} />;
    }
  };

  const getTypeBadgeClass = (type: string) => {
    switch (type) {
        case 'DOCX': return "bg-blue-100 dark:bg-blue-900/20";
        case 'XLSX': return "bg-emerald-100 dark:bg-emerald-900/20";
        case 'PPTX': return "bg-orange-100 dark:bg-orange-900/20";
        case 'PDF':  return "bg-red-100 dark:bg-red-900/20";
        default:     return "bg-muted";
    }
  };

  return (
    <div className="min-h-screen p-6 md:p-12 max-w-7xl mx-auto flex flex-col relative">
      {/* Clean Header */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-6 mb-8 shrink-0">
        <div>
          <h1 className="text-3xl md:text-4xl font-black tracking-tight mb-2">
            Documents
          </h1>
          <p className="text-muted-foreground max-w-lg leading-relaxed">
            Manage your secure documents.
          </p>
        </div>
        
        <div className="flex gap-3">
          <Button variant="outline" className="h-10 border-border bg-background hover:bg-muted rounded-none border">
            <FilterIcon size={16} className="mr-2" /> Filter
          </Button>
          <Button className="tech-btn h-10 rounded-none">
            <PlusIcon size={16} />
            Upload New
          </Button>
        </div>
      </div>

      {/* Main Content Area */}
      <div className="space-y-6 flex-1 flex flex-col">
        {/* Search Bar */}
        <div className="relative shrink-0">
           <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" size={16} />
           <input 
             type="text" 
             placeholder="Search by filename or ID..." 
             className="w-full h-12 pl-10 pr-4 bg-background border border-border focus:outline-none focus:border-primary transition-colors text-sm"
           />
        </div>

        {/* Document List Container */}
        <div className="tech-border bg-card flex-1 flex flex-col overflow-hidden relative">
          {/* Table Header */}
          <div className="shrink-0">
            <Table>
              <TableHeader>
                <TableRow className="hover:bg-transparent border-border bg-muted/30">
                  <TableHead className="w-[40px] text-center border-r border-border align-middle p-0">
                    <div className="flex items-center justify-center w-full h-full">
                      <input 
                        type="checkbox" 
                        className="accent-primary w-4 h-4 cursor-pointer"
                        checked={selectedIds.length === documents.length && documents.length > 0}
                        onChange={toggleAll}
                      />
                    </div>
                  </TableHead>
                  <TableHead className="w-[50px] text-xs font-bold uppercase tracking-wider text-muted-foreground h-10 text-center border-r border-border align-middle">ID</TableHead>
                  <TableHead className="w-[40%] text-xs font-bold uppercase tracking-wider text-muted-foreground h-10 text-center border-r border-border align-middle">Name</TableHead>
                  <TableHead className="text-xs font-bold uppercase tracking-wider text-muted-foreground h-10 text-center border-r border-border align-middle">Size</TableHead>
                  <TableHead className="text-xs font-bold uppercase tracking-wider text-muted-foreground h-10 text-center border-r border-border align-middle">Created</TableHead>
                  <TableHead className="text-xs font-bold uppercase tracking-wider text-muted-foreground h-10 text-center border-r border-border align-middle">Modified</TableHead>
                  <TableHead className="w-[80px] text-xs font-bold uppercase tracking-wider text-muted-foreground h-10 text-center align-middle">Action</TableHead>
                </TableRow>
              </TableHeader>
              {documents.length > 0 && (
                <TableBody>
                   {documents.map((doc, index) => (
                     <TableRow 
                        key={doc.id} 
                        className={`border-border hover:bg-muted/50 transition-colors cursor-pointer group h-14 ${selectedIds.includes(doc.id) ? 'bg-muted/40' : ''}`}
                        onClick={() => toggleSelection(doc.id)}
                      >
                       <TableCell className="text-center border-r border-border p-0" onClick={(e) => e.stopPropagation()}>
                         <div className="flex items-center justify-center h-full w-full py-3" onClick={() => toggleSelection(doc.id)}>
                            <input 
                              type="checkbox" 
                              className="accent-primary w-4 h-4 cursor-pointer"
                              checked={selectedIds.includes(doc.id)}
                              onChange={() => {}} 
                              onClick={(e) => {
                                e.stopPropagation(); 
                                toggleSelection(doc.id);
                              }}
                            />
                         </div>
                       </TableCell>
                       <TableCell className="text-center text-muted-foreground border-r border-border font-mono text-xs">{doc.id}</TableCell>
                       <TableCell className="font-medium text-foreground border-r border-border pl-4">
                         <div className="flex items-center gap-3">
                           {/* Brand Icon: Link to Editor */}
                           <Link 
                             href={`/editor/${doc.id}`}
                             onClick={(e) => e.stopPropagation()}
                             className={`w-8 h-8 flex items-center justify-center rounded-full ${getTypeBadgeClass(doc.type)} shrink-0 transition-transform duration-300 group-hover:scale-110 group-hover:shadow-sm`}
                           >
                              {getFileIcon(doc.type)}
                           </Link>
                           {/* Filename: Link to Editor */}
                           <Link 
                             href={`/editor/${doc.id}`}
                             onClick={(e) => e.stopPropagation()}
                             className="transition-transform duration-300 group-hover:translate-x-1 truncate select-none hover:underline underline-offset-4 decoration-primary/30"
                           >
                             {doc.name}
                           </Link>
                         </div>
                       </TableCell>
                       <TableCell className="text-center text-muted-foreground border-r border-border font-mono text-xs select-none">{doc.size}</TableCell>
                       <TableCell className="text-center text-muted-foreground border-r border-border font-mono text-xs select-none">{doc.created}</TableCell>
                       <TableCell className="text-center text-muted-foreground border-r border-border font-mono text-xs select-none">{doc.modified}</TableCell>
                       <TableCell className="text-center">
                         <Button variant="ghost" size="icon" className="h-8 w-8 hover:bg-background rounded-none" onClick={(e) => e.stopPropagation()}>
                           <MoreHorizontal size={16} className="text-muted-foreground" />
                         </Button>
                       </TableCell>
                     </TableRow>
                   ))}
                </TableBody>
              )}
            </Table>
          </div>
        </div>
      </div>

      {/* Floating Context Bar */}
      {selectedIds.length > 0 && (
        <div className="fixed bottom-8 left-1/2 -translate-x-1/2 z-50 animate-in slide-in-from-bottom-4 duration-300">
          <div className="bg-foreground text-background shadow-2xl flex items-center p-1.5 rounded-none border border-background/20 gap-1">
             <div className="pl-4 pr-3 text-xs font-mono font-bold border-r border-background/20 flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-secondary block animate-pulse"></span>
                {selectedIds.length} Selected
             </div>
             
             <Button variant="ghost" size="sm" className="h-8 text-background hover:bg-background/20 hover:text-background rounded-none text-xs font-medium px-3">
               <Download size={14} className="mr-2" />
               Download
             </Button>
             
             <Button variant="ghost" size="sm" className="h-8 text-background hover:bg-background/20 hover:text-background rounded-none text-xs font-medium px-3">
               <Share2 size={14} className="mr-2" />
               Share
             </Button>

             <div className="w-px h-4 bg-background/20 mx-1"></div>
             
             <Button variant="ghost" size="sm" className="h-8 text-red-400 hover:bg-red-400/20 hover:text-red-300 rounded-none text-xs font-medium px-3">
               <Trash2 size={14} className="mr-2" />
               Delete
             </Button>

             <Button 
               variant="ghost" 
               size="icon" 
               className="h-8 w-8 text-muted-foreground hover:bg-background/20 hover:text-background rounded-none ml-1"
               onClick={() => setSelectedIds([])}
             >
               <X size={14} />
             </Button>
          </div>
        </div>
      )}
    </div>
  );
}
