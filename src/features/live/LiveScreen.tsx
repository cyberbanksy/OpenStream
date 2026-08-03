import { Radio, SignalHigh } from "lucide-react";
import { useState } from "react";
import { useApp } from "../../app/AppContext";
import { Artwork } from "../../components/Artwork";

const channels = [
  { name: "Open Cinema", now: "Northstar", next: "The Last Signal", cell: [0, 0] as [number, number] },
  { name: "Deep Field", now: "Echoes of Mars", next: "Cinder Fall", cell: [3, 0] as [number, number] },
  { name: "Archive One", now: "Beyond the Rift", next: "Aurora", cell: [2, 1] as [number, number] },
  { name: "Documentary", now: "Silent Tides", next: "Frontier Outpost", cell: [1, 2] as [number, number] },
];

export function LiveScreen() {
  const { notify } = useApp();
  const [selected, setSelected] = useState(0);
  return (
    <main className="screen workspace-screen live-screen">
      <section className="workspace-content full-width">
        <header className="section-header"><div><h1>Live</h1><p>Channels from your configured providers</p></div><span className="live-state"><i /><SignalHigh size={17} />4 online</span></header>
        <div className="live-guide">
          <div className="guide-times"><span>Now · 8:30</span><span>9:00</span><span>9:30</span><span>10:00</span></div>
          {channels.map((channel, index) => (
            <button key={channel.name} data-focusable data-autofocus={index === 0 ? true : undefined} autoFocus={index === 0} className={selected === index ? "channel-row selected" : "channel-row"} onClick={() => { setSelected(index); notify({ tone: "neutral", title: channel.now, detail: `${channel.name} is ready to stream.` }); }}>
              <span className="channel-logo"><Radio size={21} /></span><strong>{channel.name}</strong>
              <span className="program current"><Artwork cell={channel.cell} /><b>{channel.now}</b><small>Now playing</small></span>
              <span className="program"><b>{channel.next}</b><small>Up next</small></span>
            </button>
          ))}
        </div>
      </section>
    </main>
  );
}
