'use client'

import { useState, useEffect } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { 
  ArrowLeft, 
  Save, 
  Download, 
  CheckCircle2, 
  Loader2, 
  FileText, 
  ShieldCheck, 
  Wifi,
  Clock
} from 'lucide-react'
import { Button } from '@/components/ui/button'

export default function EditorPage() {
  const params = useParams()
  const router = useRouter()
  const fileKey = params.id as string
  
  // Simulation states for "Mission Control" feel
  const [saveStatus, setSaveStatus] = useState<'idle' | 'saving' | 'saved'>('idle')
  const [lastSaved, setLastSaved] = useState<Date | null>(null)
  const [currentTime, setCurrentTime] = useState<string>("--:--:--")

  // Clock Ticker
  useEffect(() => {
    setCurrentTime(new Date().toLocaleTimeString('en-US', { hour12: false }));
    const timer = setInterval(() => {
        setCurrentTime(new Date().toLocaleTimeString('en-US', { hour12: false }))
    }, 1000)
    return () => clearInterval(timer)
  }, [])

  // Save Handler Simulation
  const handleSave = () => {
    setSaveStatus('saving')
    // Simulate Network Request
    setTimeout(() => {
      setSaveStatus('saved')
      setLastSaved(new Date())
      // Reset to idle after 2 seconds
      setTimeout(() => setSaveStatus('idle'), 2000)
    }, 1500)
  }

  return (
    <div className="flex flex-col h-screen w-full bg-background overflow-hidden font-sans">
      
      {/* 1. Mission Control Header */}
      <header className="h-14 border-b border-border flex items-center justify-between px-0 bg-background z-10 shrink-0">
        
        {/* Left: Navigation & Info */}
        <div className="flex items-center h-full">
          <Button 
            onClick={() => router.back()} 
            variant="ghost" 
            className="h-full w-14 rounded-none border-r border-border hover:bg-muted text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft size={20} />
          </Button>

          <div className="px-6 flex flex-col justify-center h-full">
            <div className="flex items-center gap-3">
               <FileText size={16} className="text-blue-600" />
               <span className="font-bold text-sm tracking-tight text-foreground">Technical_Spec_v2.docx</span>
               <span className="px-1.5 py-0.5 rounded-sm bg-muted text-[10px] font-mono text-muted-foreground border border-border flex items-center gap-1">
                 <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
                 v2.4
               </span>
            </div>
          </div>
        </div>

        {/* Right: Actions */}
        <div className="flex items-center h-full px-4 gap-3">
           
           {/* Last Saved Indicator (Fade In/Out) */}
           <div className={`hidden md:flex items-center gap-1.5 text-xs text-muted-foreground mr-2 transition-opacity duration-300 ${lastSaved ? 'opacity-100' : 'opacity-0'}`}>
              <CheckCircle2 size={12} className="text-emerald-500" />
              <span>Saved {lastSaved?.toLocaleTimeString()}</span>
           </div>

           <div className="h-6 w-px bg-border mx-1 hidden md:block"></div>

           <Button variant="outline" size="sm" className="h-9 rounded-none border-border hover:bg-muted gap-2 text-xs font-medium hidden md:flex">
             <Download size={14} />
             Export
           </Button>

           <Button 
             onClick={handleSave}
             disabled={saveStatus === 'saving'}
             className={`h-9 rounded-none min-w-[100px] text-xs font-bold gap-2 transition-all duration-200 border border-transparent ${
                saveStatus === 'saved' 
                ? 'bg-emerald-600 hover:bg-emerald-700 text-white border-emerald-500' 
                : 'bg-primary text-primary-foreground hover:bg-primary/90'
             }`}
           >
             {saveStatus === 'saving' ? (
               <>
                 <Loader2 size={14} className="animate-spin" />
                 Saving...
               </>
             ) : saveStatus === 'saved' ? (
               <>
                 <CheckCircle2 size={14} />
                 Saved
               </>
             ) : (
               <>
                 <Save size={14} />
                 Save
               </>
             )}
           </Button>
        </div>
      </header>

      {/* 2. Main Editor Area */}
      <main className="flex-1 relative bg-muted/10 flex flex-col">
        {/* Editor Placeholder / Container */}
        <div className="flex-1 w-full h-full bg-white relative flex flex-col items-center justify-center">
           {/* In a real app, <DocumentEditor /> goes here */}
           <div className="text-center p-8 max-w-md animate-in zoom-in-95 duration-500 fade-in">
              <div className="relative w-16 h-16 mx-auto mb-6">
                 <div className="absolute inset-0 bg-blue-100 dark:bg-blue-900/20 rounded-full animate-ping opacity-20"></div>
                 <div className="relative w-16 h-16 bg-blue-50 dark:bg-blue-900/20 rounded-full flex items-center justify-center border border-blue-100 dark:border-blue-800">
                    <Loader2 size={28} className="text-blue-600 animate-spin" />
                 </div>
              </div>
              
              <h2 className="text-lg font-bold text-foreground mb-2">Loading Editor...</h2>
              <p className="text-sm text-muted-foreground">
                Initializing document environment
              </p>
           </div>
        </div>
      </main>

      {/* 3. Industrial Status Bar */}
      <footer className="h-8 bg-foreground text-background border-t border-border flex items-center justify-between px-4 text-[10px] font-mono select-none z-20">
         {/* Left Status */}
         <div className="flex items-center gap-6 h-full">
            <div className="flex items-center gap-2 opacity-90">
               <div className={`w-2 h-2 rounded-full transition-colors duration-300 ${saveStatus === 'saving' ? 'bg-orange-500 animate-pulse' : 'bg-emerald-500'}`}></div>
               <span className="uppercase tracking-widest font-bold">{saveStatus === 'saving' ? 'PROCESSING' : 'READY'}</span>
            </div>
            
            <div className="w-px h-3 bg-background/20"></div>
            
            <span className="opacity-60">READ-WRITE</span>
         </div>

         {/* Right Telemetry */}
         <div className="flex items-center gap-6 h-full">
             <div className="flex items-center gap-2 opacity-60">
               <Wifi size={12} />
               <span>Online</span>
             </div>

             <div className="w-px h-3 bg-background/20"></div>

             <div className="flex items-center gap-2 opacity-60">
               <ShieldCheck size={12} />
               <span>Secure</span>
             </div>

             <div className="w-px h-3 bg-background/20"></div>

             <div className="flex items-center gap-2 min-w-[70px] justify-end opacity-90 font-bold">
               <Clock size={12} />
               <span suppressHydrationWarning>{currentTime}</span>
             </div>
         </div>
      </footer>
    </div>
  )
}
