with open('src/data/campaign/intel/OS_SpoonDirectoryIntel.java', 'r') as f:
    c = f.read()

c = c.replace('Sindrian Diktat: Volturnian Lobster Feast (600 credits)', 'Sindrian Diktat: Volturnian Lobster Feast (200 credits)')
c = c.replace('Luddic Church: Pilgrim\'s Hearth Harvest Feast (200 credits)', 'Luddic Church: Pilgrim\'s Hearth Harvest Feast (200 credits)')
c = c.replace('Persean League: Archon\'s Grand Mezze Banquet (350 credits)', 'Persean League: Archon\'s Grand Mezze Banquet (200 credits)')
c = c.replace('Tri-Tachyon: Executive Synth-Steak Suite (450 credits)', 'Tri-Tachyon: Executive Synth-Steak Suite (200 credits)')
c = c.replace('Hegemony: Commissary Auxiliary Rations (150 credits)', 'Hegemony: Commissary Auxiliary Rations (200 credits)')
c = c.replace('Pirates: Fringe Scavenger Platter (100 credits)', 'Pirates: Fringe Scavenger Platter (200 credits)')
c = c.replace('Luddic Path: Ascetic Penance Broth (50 credits)', 'Luddic Path: Ascetic Penance Broth (200 credits)')
c = c.replace('Luddic Path: Ascetic Penance Rations (50 credits)', 'Luddic Path: Ascetic Penance Rations (200 credits)')
c = c.replace('(Flat 50 to 600 Credits)', '(Flat 200 Credits)')

with open('src/data/campaign/intel/OS_SpoonDirectoryIntel.java', 'w') as f:
    f.write(c)
